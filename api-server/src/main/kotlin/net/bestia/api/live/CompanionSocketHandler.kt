package net.bestia.api.live

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.bestia.api.auth.CompanionTokenVerifier
import net.bestia.api.metrics.LiveMetrics
import net.bestia.bus.contract.ZoneEvent
import net.bestia.bus.valkey.ValkeyStreamReader
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

/**
 * `/v1/companion/ws`. The first frame must be `{"type":"auth","token":...,"lastEventId":...}`; until then the
 * socket hears nothing. After it, the server sends what the account missed since `lastEventId` (as far as the
 * stream still holds), then `{"type":"ready"}`, then every new event as `{"type":"event",...}`.
 *
 * The app never says which events it wants: they are the account's own, by the token.
 */
@Component
class CompanionSocketHandler(
  private val verifier: CompanionTokenVerifier,
  private val sessions: LiveSessions,
  private val events: ValkeyStreamReader<ZoneEvent>,
  private val metrics: LiveMetrics,
) : TextWebSocketHandler() {

  private data class Hello(val type: String, val token: String?, val lastEventId: String?)

  data class Ready(val type: String = "ready", val lastEventId: String)

  data class EventFrame(val type: String = "event", val event: LiveEvent)

  private val mapper = jacksonObjectMapper()

  override fun handleTextMessage(socket: WebSocketSession, message: TextMessage) {
    if (socket.attributes[ACCOUNT] != null) {
      // Nothing is read after the hello; the socket only carries the server's events.
      return
    }

    val hello = try {
      mapper.readValue<Hello>(message.payload)
    } catch (_: Exception) {
      null
    }
    val verified = hello?.takeIf { it.type == "auth" }?.token?.let { verifier.verify(it) }
    if (verified == null) {
      metrics.socketAuth(LiveMetrics.SocketAuth.UNAUTHORIZED)
      socket.close(UNAUTHORIZED)
      return
    }

    metrics.socketAuth(LiveMetrics.SocketAuth.OK)
    socket.attributes[ACCOUNT] = verified.accountId
    val safe = sessions.add(verified.accountId, socket, verified.expiresAt)

    hello.lastEventId?.let { last ->
      val missed = events.since(last, verified.accountId)
      metrics.replayed(missed.size)
      missed.forEach { sessions.send(safe, EventFrame(event = LiveEvent.of(it))) }
    }
    sessions.send(safe, Ready(lastEventId = events.newestId()))
  }

  override fun afterConnectionClosed(socket: WebSocketSession, status: CloseStatus) {
    (socket.attributes[ACCOUNT] as? Long)?.let { sessions.remove(it, socket) }
  }

  private companion object {
    const val ACCOUNT = "accountId"
    val UNAUTHORIZED = CloseStatus(4000, "unauthorized")
  }
}
