package stomp

import java.util.*

data class StompMessage(
    val stompCommand: StompCommand,
    val headers: StompHeaders = StompHeaders(listOf()),
    val payload: ByteArray = byteArrayOf()
) {
    val isHeartbeat = stompCommand == StompCommand.HEARTBEAT

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StompMessage

        if (!Arrays.equals(payload, other.payload)) return false
        if (headers != other.headers) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(payload)
        result = 31 * result + headers.hashCode()
        return result
    }

    companion object {
        internal val HEARTBEAT_PAYLOAD = byteArrayOf('\n'.toByte())
    }
}