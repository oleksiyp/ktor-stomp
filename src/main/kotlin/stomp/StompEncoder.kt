package stomp

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException

class StompEncoder {
    fun encode(message: StompMessage): ByteArray {
        val command = message.stompCommand
        val headers = message.headers
        val payload = message.payload
        try {
            val baos = ByteArrayOutputStream(128 + payload.size)
            val output = DataOutputStream(baos)

            if (message.isHeartbeat) {
                output.write(message.payload)
            } else {
                output.write(command.toString().toByteArray(Charsets.UTF_8))
                output.write(LF.toInt())
                writeHeaders(command, headers, payload, output)
                output.write(LF.toInt())
                writeBody(payload, output)
                output.write(0)
            }

            return baos.toByteArray()
        } catch (ex: IOException) {
            throw RuntimeException("Failed to encode STOMP frame, headers=$headers", ex)
        }

    }

    private fun writeHeaders(
        command: StompCommand,
        headers: StompHeaders,
        payload: ByteArray,
        output: DataOutputStream
    ) {
        val shouldEscape = command !== StompCommand.CONNECT && command !== StompCommand.CONNECTED

        for (entry in headers.list.groupBy { it.first }) {
            if (command.isBodyAllowed && "content-length" == entry.key) {
                continue
            }

            val values = entry.value.map { it.second }
            val encodedKey = encodeHeaderKey(entry.key, shouldEscape)
            for (value in values) {
                output.write(encodedKey)
                output.write(COLON.toInt())
                output.write(encodeHeaderValue(value, shouldEscape))
                output.write(LF.toInt())
            }
        }

        if (command.isBodyAllowed) {
            val contentLength = payload.size
            output.write("content-length:".toByteArray(Charsets.UTF_8))
            output.write(Integer.toString(contentLength).toByteArray(Charsets.UTF_8))
            output.write(LF.toInt())
        }
    }

    private fun encodeHeaderKey(input: String, escape: Boolean): ByteArray {
        val inputToUse = if (escape) escape(input) else input
        return inputToUse.toByteArray(Charsets.UTF_8)
    }

    private fun encodeHeaderValue(input: String, escape: Boolean): ByteArray {
        val inputToUse = if (escape) escape(input) else input
        return inputToUse.toByteArray(Charsets.UTF_8)
    }

    private fun escape(inString: String): String {
        var sb: StringBuilder? = null
        for (i in 0 until inString.length) {
            val c = inString[i]
            when {
                c == '\\' -> {
                    sb = getStringBuilder(sb, inString, i)
                    sb.append("\\\\")
                }
                c == ':' -> {
                    sb = getStringBuilder(sb, inString, i)
                    sb.append("\\c")
                }
                c == '\n' -> {
                    sb = getStringBuilder(sb, inString, i)
                    sb.append("\\n")
                }
                c == '\r' -> {
                    sb = getStringBuilder(sb, inString, i)
                    sb.append("\\r")
                }
                sb != null -> sb.append(c)
            }
        }
        return if (sb != null) sb.toString() else inString
    }

    private fun getStringBuilder(sb: StringBuilder?, inString: String, i: Int) =
        sb ?: StringBuilder(inString.length)
            .apply { append(inString.substring(0, i)) }

    @Throws(IOException::class)
    private fun writeBody(payload: ByteArray, output: DataOutputStream) {
        output.write(payload)
    }

    companion object {
        private val LF: Byte = '\n'.toByte()
        private val COLON: Byte = ':'.toByte()
    }
}
