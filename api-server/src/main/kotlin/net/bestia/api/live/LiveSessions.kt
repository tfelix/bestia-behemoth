package net.bestia.api.live

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import net.bestia.api.metrics.LiveMetrics
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PingMessage
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.SessionLimitExceededException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * The open sockets of this instance, by account. A socket lives as long as the token it opened with; once that
 * expires it is closed with [TOKEN_EXPIRED], and the app reconnects with a fresh one.
 */
@Component
class LiveSessions(private val metrics: LiveMetrics, registry: MeterRegistry) {

  private class Live(val socket: WebSocketSession, val expiresAt: Instant)

  private val byAccount = ConcurrentHashMap<Long, MutableMap<String, Live>>()
  private val mapper = jacksonObjectMapper()

  init {
    Gauge.builder("api.ws.sessions", byAccount) { accounts -> accounts.values.sumOf { it.size }.toDouble() }
      .description("Companion sockets open on this instance")
      .register(registry)
    Gauge.builder("api.ws.accounts", byAccount) { it.size.toDouble() }
      .description("Accounts with at least one companion socket open on this instance")
      .register(registry)
  }

  fun add(accountId: Long, socket: WebSocketSession, expiresAt: Instant): WebSocketSession {
    val safe = ConcurrentWebSocketSessionDecorator(socket, SEND_TIME_LIMIT_MILLIS, SEND_BUFFER_BYTES)
    byAccount.computeIfAbsent(accountId) { ConcurrentHashMap() }[socket.id] = Live(safe, expiresAt)
    return safe
  }

  fun remove(accountId: Long, socket: WebSocketSession) {
    byAccount.computeIfPresent(accountId) { _, sockets ->
      sockets.remove(socket.id)
      sockets.ifEmpty { null }
    }
  }

  fun isConnected(accountId: Long): Boolean {
    return byAccount[accountId]?.isNotEmpty() == true
  }

  fun send(accountId: Long, frame: Any) {
    val sockets = byAccount[accountId] ?: return
    val text = TextMessage(mapper.writeValueAsString(frame))

    for (live in sockets.values) {
      send(live.socket, text)
    }
  }

  fun send(socket: WebSocketSession, frame: Any) {
    send(socket, TextMessage(mapper.writeValueAsString(frame)))
  }

  /** Keeps idle sockets open through proxies, and closes those whose token has run out. */
  @Scheduled(fixedRate = KEEP_ALIVE_MILLIS)
  fun keepAlive() {
    val now = Instant.now()
    for (sockets in byAccount.values) {
      for (live in sockets.values) {
        try {
          if (live.expiresAt.isBefore(now)) {
            live.socket.close(TOKEN_EXPIRED)
            metrics.expired()
          } else {
            live.socket.sendMessage(PingMessage())
          }
        } catch (e: Exception) {
          metrics.keepAliveFailed()
          LOG.debug { "Keep-alive on socket ${live.socket.id} failed: ${e.message}" }
        }
      }
    }
  }

  private fun send(socket: WebSocketSession, text: TextMessage) {
    try {
      socket.sendMessage(text)
      metrics.frameSent()
    } catch (e: Exception) {
      metrics.sendFailed(if (e is SessionLimitExceededException) LiveMetrics.SendFailure.OVERFLOW else LiveMetrics.SendFailure.OTHER)
      LOG.debug { "Sending to socket ${socket.id} failed: ${e.message}" }
    }
  }

  companion object {
    /** A close code from the private range: the app reads it as "refresh the token and reconnect". */
    val TOKEN_EXPIRED = CloseStatus(4001, "token expired")

    private const val KEEP_ALIVE_MILLIS = 25_000L
    private const val SEND_TIME_LIMIT_MILLIS = 5_000
    private const val SEND_BUFFER_BYTES = 256 * 1024
    private val LOG = KotlinLogging.logger { }
  }
}
