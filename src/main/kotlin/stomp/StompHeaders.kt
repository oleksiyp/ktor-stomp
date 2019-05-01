package stomp

data class StompHeaders(val list: List<Pair<String, String>> = listOf()) {
    private fun getFirstHeader(name: String) = list.firstOrNull {
        it.first.equals(name, true)
    }?.second

    val contentLength: Int?
        get() = getFirstHeader(CONTENT_LENGTH)?.toIntOrNull()

    val destination: String?
        get() = getFirstHeader(DESTINATION)

    val id: String?
        get() = getFirstHeader("id")

    val receipt: String?
        get() = getFirstHeader(RECEIPT)

    companion object {
        val CONTENT_LENGTH = "content-length"
        val RECEIPT = "receipt-id"
        val RECEIPT_ID = "receipt-id"
        val MESSAGE = "message"
        val MESSAGE_ID = "message-id"
        val SUBSCRIPTION = "subscription"
        val DESTINATION = "destination"
    }
}