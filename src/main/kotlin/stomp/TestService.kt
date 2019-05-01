package stomp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.http.HttpMethod
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets
import jdk.incubator.http.WebSocket

fun Application.pingPongService() {
    routing {
        route("/api") {
            route("/signals", HttpMethod.Get) {
                val regex = Regex("/([a-zA-Z0-9]{8})")
                stomp {
                    val matcher = regex.matchEntire(this.destination)
                        ?: throw RuntimeException("bad subscription destination")

                    val id = matcher.groupValues[1]

                    val mapper = ObjectMapper()
                        .registerKotlinModule()
                        .enable(SerializationFeature.INDENT_OUTPUT)

                    sendAll(mapper.writeValueAsBytes("SUBSCRIBED " + id))

                    for (message in incomingMessages) {
                        sendAll(mapper.writeValueAsBytes("PONG " + String(message.payload)))
                    }
                }
            }
        }
    }
}

fun main(args: Array<String>) {
    val server = embeddedServer(Netty, 8080) {
        install(WebSockets)
        pingPongService()
    }
    server.start(wait = true)
}