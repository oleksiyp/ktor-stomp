package stomp

import kotlinx.coroutines.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class StompContext(val handler: suspend StompSession.() -> Unit) {
    private val lock = ReentrantLock()
    private val sessions = mutableMapOf<String, StompSession>()

    internal fun addSource(destination: String, source: StompSource) {
        lock.withLock {
            val session = sessions[destination] ?: startNewSession(destination)
            session.addSource(source)
        }
    }

    fun session(destination: String) = lock.withLock { sessions[destination] }

    internal fun removeSource(connection: StompConnection, subscriptionId: String) {
        val tasks = lock.withLock {
            sessions.forEach { (_, session) ->
                session.removeSource(connection, subscriptionId)
            }
            val empty = sessions.filter { (_, session) ->
                session.internalSources.isEmpty()
            }
            empty.map { (destination, session) ->
                GlobalScope.async {
                    sessions.remove(destination)
                    session.cancel()
                }
            }
        }

        runBlocking {
            tasks.forEach { it.await() }
        }
    }

    private fun startNewSession(destination: String): StompSession {
        val session = StompSession(destination, this)
        GlobalScope.launch {
            session.job = coroutineContext[Job]!!
            session.handler()
            lock.withLock {
                sessions.remove(destination)
                session.cancel()
            }
        }
        sessions[destination] = session
        return session
    }

    private fun close() {
        lock.withLock {
            runBlocking {
                sessions.values.map {
                    async { it.cancel() }
                }.forEach { it.await() }
            }
            sessions.clear()
        }
    }

}