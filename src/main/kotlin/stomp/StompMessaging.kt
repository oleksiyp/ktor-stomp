package stomp

import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.FrameType
import io.ktor.routing.Route
import io.ktor.websocket.webSocket
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.produce
import stomp.StompCommand.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.ByteBuffer

fun Route.rawStomp(handler: suspend RawStompContext.() -> Unit) {
    val decoder = StompDecoder()
    val bufferingDecoder = BufferingStompDecoder(decoder, 1024)
    val encoder = StompEncoder()
    webSocket(protocol = "v11.stomp") {
        val incomingMessages = produce {
            for (frame in incoming) {
                for (message in bufferingDecoder.decode(frame.buffer)) {
                    send(message)
                }
            }
        }

        val outgoingMessages = actor<StompMessage> {
            for (message in channel) {
                val buffer = ByteBuffer.wrap(encoder.encode(message))
                outgoing.send(Frame.byType(true, FrameType.BINARY, buffer))
            }
            close()
        }

        RawStompContext(incomingMessages, outgoingMessages).handler()
    }
}


fun Route.stomp(printStacks: Boolean = true, handler: suspend StompSession.() -> Unit) {
    val context = StompContext(handler)
    rawStomp {
        val connection = StompConnection()
        msgLoop@ for (message in incomingMessages) {
            val connect: suspend () -> Unit = {
                outgoingMessages.send(
                    StompMessage(
                        CONNECTED, StompHeaders(
                            listOf(
                                "version" to "1.1",
                                "session" to connection.id,
                                "server" to "ktor.io/1.0",
                                "heart-beat" to "10000, 10000"
                            )
                        )
                    )
                )
            }

            val subscribe: suspend () -> Unit = {
                val destination = message.headers.destination
                        ?: throw RuntimeException("no destination header provided")

                val subscriptionId = message.headers.id
                        ?: throw RuntimeException("no id header provided")

                val sendMessage: suspend (StompMessage) -> Unit = {
                    outgoingMessages.send(it)
                }

                context.addSource(
                    destination,
                    StompSource(
                        destination,
                        connection,
                        subscriptionId,
                        sendMessage
                    )
                )
            }

            val unsubscribe: suspend () -> Unit = {
                val subscriptionId = message.headers.id
                        ?: throw RuntimeException("no id header provided")

                context.removeSource(
                    connection,
                    subscriptionId
                )
            }


            val send: suspend () -> Unit = {
                val destination = message.headers.destination
                        ?: throw RuntimeException("no destination header provided")


                val session = context.session(destination)
                        ?: throw RuntimeException("subscription '$destination' not found")

                session.internalIncomingMessages.send(message)
            }

            val replyReceipt: suspend () -> Unit = {
                val headers = StompMutableHeaders(mutableListOf())

                val receiptId = message.headers.receipt
                if (receiptId != null) {
                    headers.list.add(StompHeaders.RECEIPT_ID to receiptId)
                }

                outgoingMessages.send(
                    StompMessage(
                        RECEIPT,
                        headers.toHeaders()
                    )
                )
            }

            val replyPong: suspend () -> Unit = {
                outgoingMessages.send(StompMessage(HEARTBEAT))
            }

            try {
                when (message.stompCommand) {
                    STOMP -> connect()
                    CONNECT -> connect()
                    DISCONNECT -> {
                        replyReceipt()
                        break@msgLoop
                    }
                    SUBSCRIBE -> subscribe()
                    UNSUBSCRIBE -> unsubscribe()
                    SEND -> send()
                    ACK -> TODO()
                    NACK -> TODO()
                    BEGIN -> TODO()
                    COMMIT -> TODO()
                    ABORT -> TODO()
                    HEARTBEAT -> replyPong()
                    else -> RuntimeException("not server command in $message")
                }
            } catch (ex: Exception) {
                val shortMessage = ex.message ?: "internal server error"
                val details = ByteArrayOutputStream()
                PrintStream(details).use {
                    if (printStacks) {
                        ex.printStackTrace(it)
                    } else {
                        it.bufferedWriter().write(ex.toString())
                    }
                }

                val headers = StompMutableHeaders(mutableListOf())

                val receiptId = message.headers.receipt
                if (receiptId != null) {
                    headers.list.add(StompHeaders.RECEIPT_ID to receiptId)
                }
                headers.list.add(StompHeaders.MESSAGE to shortMessage)

                outgoingMessages.send(
                    StompMessage(
                        ERROR,
                        headers.toHeaders(),
                        details.toByteArray()
                    )
                )

                break@msgLoop
            }
        }
    }
}