package stomp

data class StompMutableHeaders(val list: MutableList<Pair<String, String>>) {
    fun toHeaders() = StompHeaders(list.toList())

    fun put(key: String, value: String) {
        list.removeIf { it.first == key }
        list.add(key to value)
    }
}