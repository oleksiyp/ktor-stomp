package stomp

class StompSource(
    val destination: String,
    val connection: StompConnection,
    val subscriptionId: String,
    internal val internalSendCallback: suspend (StompMessage) -> Unit
) {
    suspend fun send(payload: ByteArray, headers: StompHeaders = StompHeaders()) {
        val message = StompMessage(StompCommand.MESSAGE, headers, payload)
        val messageId = connection.nextMessageId()

        internalSend(message, messageId)
    }

    internal suspend fun internalSend(
        message: StompMessage,
        messageId: String
    ) {
        val newHeaders = StompMutableHeaders(mutableListOf())
        newHeaders.list.addAll(message.headers.list)

        newHeaders.put(StompHeaders.MESSAGE_ID, messageId)
        newHeaders.put(StompHeaders.SUBSCRIPTION, subscriptionId)
        newHeaders.put(StompHeaders.DESTINATION, destination)

        val newMessage = message.copy(headers = newHeaders.toHeaders())

        internalSendCallback(newMessage)
    }

    internal var sessionInternal: StompSession? = null
        set(value) {
            if (field != null) throw RuntimeException("not able to bind session two times")
            field = value
        }

    val session: StompSession
        get() = sessionInternal!!


}