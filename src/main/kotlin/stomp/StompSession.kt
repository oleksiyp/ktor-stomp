package stomp

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class StompSession(
    val destination: String,
    val context: StompContext
) {
    internal fun addSource(source: StompSource) {
        lock.withLock {
            source.sessionInternal = this
            if (internalSources.any {
                    it.connection.id == source.connection.id &&
                            it.subscriptionId == source.subscriptionId
                }) {
                throw RuntimeException("already subscribed with ID ${source.subscriptionId}")
            }
            internalSources.add(source)
        }
    }

    internal fun removeSource(connection: StompConnection, subscriptionId: String) {
        lock.withLock {
            internalSources.removeIf {
                it.connection == connection &&
                        it.subscriptionId == subscriptionId
            }
        }
    }

    suspend fun sendAll(payload: ByteArray, headers: StompHeaders = StompHeaders()) {
        val message = StompMessage(StompCommand.MESSAGE, headers, payload)
        val tasks = sources
            .groupBy { it.connection }
            .map { (conn, oneConnectionSources) ->
                val messageId = conn.nextMessageId()

                GlobalScope.async {
                    oneConnectionSources.map {
                        it.internalSend(message, messageId)
                    }
                }
            }

        tasks.forEach { it.await() }
    }

    internal val internalIncomingMessages = Channel<StompMessage>()
    val incomingMessages: ReceiveChannel<StompMessage>
        get() = internalIncomingMessages

    fun cancel() {
        lock.withLock {
            internalSources.clear()
            job.cancel()
            internalIncomingMessages.close()
        }
    }

    internal lateinit var job: Job
    private val lock = ReentrantLock()
    internal val internalSources = mutableListOf<StompSource>()

    val sources: List<StompSource>
        get() = lock.withLock { internalSources.toList() }

    val isActive: Boolean
        get() = job.isActive

}