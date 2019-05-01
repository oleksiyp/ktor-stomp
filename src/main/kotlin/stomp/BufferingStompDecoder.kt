package stomp

import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

class BufferingStompDecoder(val stompDecoder: StompDecoder, val bufferSizeLimit: Int) {

    private val chunks = LinkedBlockingQueue<ByteBuffer>()

    @Volatile
    var expectedContentLength: Int? = null
        private set

    val bufferSize: Int
        get() = chunks.sumBy { it.remaining() }


    fun decode(newBuffer: ByteBuffer): List<StompMessage> {
        this.chunks.add(newBuffer)
        checkBufferLimits()

        val contentLength = this.expectedContentLength
        if (contentLength != null && bufferSize < contentLength) {
            return listOf()
        }

        val bufferToDecode = assembleChunksAndReset()
        val headers = StompMutableHeaders(mutableListOf())
        val messages = this.stompDecoder.decode(bufferToDecode, headers)

        if (bufferToDecode.hasRemaining()) {
            chunks.add(bufferToDecode)
            expectedContentLength = headers.toHeaders().contentLength
        }

        return messages
    }

    private fun assembleChunksAndReset(): ByteBuffer {
        val result: ByteBuffer
        if (this.chunks.size == 1) {
            result = this.chunks.remove()
        } else {
            result = ByteBuffer.allocate(bufferSize)
            for (partial in this.chunks) {
                result.put(partial)
            }
            result.flip()
        }
        this.chunks.clear()
        expectedContentLength = null
        return result
    }

    private fun checkBufferLimits() {
        val contentLength = this.expectedContentLength
        if (contentLength != null && contentLength > this.bufferSizeLimit) {
            throw RuntimeException(
                "STOMP 'content-length' header value " + this.expectedContentLength +
                        "  exceeds configured buffer size limit " + this.bufferSizeLimit
            )
        }
        if (bufferSize > this.bufferSizeLimit) {
            throw RuntimeException(
                ("The configured STOMP buffer size limit of " +
                        this.bufferSizeLimit + " bytes has been exceeded")
            )
        }
    }
}