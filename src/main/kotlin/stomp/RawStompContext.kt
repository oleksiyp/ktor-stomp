package stomp

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

class RawStompContext(
    val incomingMessages: ReceiveChannel<StompMessage>,
    val outgoingMessages: SendChannel<StompMessage>
)