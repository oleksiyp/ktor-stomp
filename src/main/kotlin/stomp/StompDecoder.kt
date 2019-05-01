package stomp

import java.io.ByteArrayOutputStream
import java.nio.Buffer
import java.nio.ByteBuffer

class StompDecoder {
    fun decode(
        byteBuffer: ByteBuffer,
        partialMessageHeaders: StompMutableHeaders
    ): List<StompMessage> {

        val messages = mutableListOf<StompMessage>()
        while (byteBuffer.hasRemaining()) {
            messages.add(
                decodeMessage(byteBuffer, partialMessageHeaders)
                        ?: break
            )

        }
        return messages
    }

    private fun decodeMessage(
        byteBuffer: ByteBuffer,
        partialHeaders: StompMutableHeaders
    ): StompMessage? {
        skipLeadingEol(byteBuffer)

        byteBuffer.mark()

        val command = readCommand(byteBuffer)
        if (command.isEmpty()) {
            return StompMessage(
                StompCommand.HEARTBEAT,
                StompHeaders(listOf()),
                StompMessage.HEARTBEAT_PAYLOAD
            )
        }

        val headers = StompMutableHeaders(mutableListOf())
        val stompCommand = StompCommand.valueOf(command)
        if (byteBuffer.remaining() <= 0) {
            return null
        }

        readHeaders(byteBuffer, headers)
        val payload = readPayload(byteBuffer, headers.toHeaders())
        if (payload == null) {
            partialHeaders.list.addAll(headers.list)
            byteBuffer.reset()
            return null
        }

        if (payload.isNotEmpty()) {
            if (!stompCommand.isBodyAllowed) {
                throw RuntimeException(
                    stompCommand.toString() +
                            " shouldn't have a payload: length=" +
                            payload.size +
                            ", headers=" +
                            partialHeaders
                )
            }
        }
        return StompMessage(stompCommand, headers.toHeaders(), payload)
    }

    protected fun skipLeadingEol(byteBuffer: ByteBuffer) {
        while (true) {
            if (!tryConsumeEndOfLine(byteBuffer)) {
                break
            }
        }
    }

    private fun readCommand(byteBuffer: ByteBuffer): String {
        val command = ByteArrayOutputStream(256)
        while (byteBuffer.remaining() > 0 && !tryConsumeEndOfLine(byteBuffer)) {
            command.write(byteBuffer.get().toInt())
        }
        return String(command.toByteArray(), Charsets.UTF_8)
    }

    private fun readHeaders(byteBuffer: ByteBuffer, headers: StompMutableHeaders) {
        while (true) {
            val headerStream = ByteArrayOutputStream(256)
            var headerComplete = false

            while (byteBuffer.hasRemaining()) {
                if (tryConsumeEndOfLine(byteBuffer)) {
                    headerComplete = true
                    break
                }
                headerStream.write(byteBuffer.get().toInt())
            }

            if (headerStream.size() <= 0 || !headerComplete) {
                break
            }

            val header = String(headerStream.toByteArray(), Charsets.UTF_8)
            val colonIndex = header.indexOf(':')
            if (colonIndex <= 0) {
                if (byteBuffer.remaining() > 0) {
                    throw RuntimeException(
                        ("Illegal header: '" + header +
                                "'. A header must be of the form <name>:[<value>].")
                    )
                }
            } else {
                val headerName = unescape(header.substring(0, colonIndex))
                val headerValue = unescape(header.substring(colonIndex + 1))
                headers.list.add(Pair(headerName, headerValue))
            }
        }
    }

    private fun unescape(inString: String): String {
        val sb = StringBuilder(inString.length)
        var pos = 0  // position in the old string
        var index = inString.indexOf('\\')

        while (index >= 0) {
            sb.append(inString.substring(pos, index))
            if (index + 1 >= inString.length) {
                throw RuntimeException("Illegal escape sequence at index $index: $inString")
            }
            val c = inString[index + 1]
            when (c) {
                'r' -> sb.append('\r')
                'n' -> sb.append('\n')
                'c' -> sb.append(':')
                '\\' -> sb.append('\\')
                else -> throw RuntimeException("Illegal escape sequence at index $index: $inString")
            }
            pos = index + 2
            index = inString.indexOf('\\', pos)
        }

        sb.append(inString.substring(pos))
        return sb.toString()
    }

    private fun readPayload(byteBuffer: ByteBuffer, headers: StompHeaders): ByteArray? {
        val contentLength = headers.contentLength

        if (contentLength != null && contentLength >= 0) {
            if (byteBuffer.remaining() > contentLength) {
                val payload = ByteArray(contentLength)
                byteBuffer.get(payload)
                if (byteBuffer.get().toInt() != 0) {
                    throw RuntimeException("Frame must be terminated with a null octet")
                }
                return payload
            } else {
                return null
            }
        } else {
            val payload = ByteArrayOutputStream(256)
            while (byteBuffer.remaining() > 0) {
                val b = byteBuffer.get()
                if (b.toInt() == 0) {
                    return payload.toByteArray()
                } else {
                    payload.write(b.toInt())
                }
            }
        }
        return null
    }

    private fun tryConsumeEndOfLine(byteBuffer: ByteBuffer): Boolean {
        if (byteBuffer.remaining() > 0) {
            val b = byteBuffer.get()
            if (b == '\n'.toByte()) {
                return true
            } else if (b == '\r'.toByte()) {
                return if (byteBuffer.remaining() > 0 && byteBuffer.get() == '\n'.toByte()) {
                    true
                } else {
                    throw RuntimeException("'\\r' must be followed by '\\n'")
                }
            }
            (byteBuffer as Buffer).position(byteBuffer.position() - 1)
        }
        return false
    }

}