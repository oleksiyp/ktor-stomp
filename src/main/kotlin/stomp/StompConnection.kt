package stomp

import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class StompConnection(
    val id: String = randomId()
) {
    private val messageId = AtomicInteger()
    fun nextMessageId() = "message-${messageId.incrementAndGet()}"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StompConnection

        return id == other.id

    }

    override fun hashCode() = id.hashCode()

    companion object {
        private val rnd = Random()
        private val idAbc = ('0'..'9') + ('a'..'z') + ('A'..'Z')
        private fun randomId(n: Int = 16): String {
            return (1..n).map { idAbc[rnd.nextInt(idAbc.size)] }.joinToString("")
        }
    }


}