package stomp


enum class StompCommand(val isBodyAllowed: Boolean = false) {
    // client
    STOMP,
    CONNECT,
    DISCONNECT,
    SUBSCRIBE,
    UNSUBSCRIBE,
    SEND(true),
    ACK,
    NACK,
    BEGIN,
    COMMIT,
    ABORT,

    // server
    CONNECTED,
    RECEIPT,
    MESSAGE(true),
    ERROR(true),

    // speical type of command
    HEARTBEAT
}

