package net.bestia.api.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import net.bestia.bus.contract.ZoneEvent
import org.springframework.stereotype.Component

/** The companion socket and the event fan-out behind it. The live-socket gauges are `LiveSessions`'s own. */
@Component
class LiveMetrics(private val registry: MeterRegistry) {

  /** What the first frame of a socket came to. */
  enum class SocketAuth { OK, UNAUTHORIZED }

  /** Why a frame could not be sent to a socket. */
  enum class SendFailure { OVERFLOW, OTHER }

  /** What a zone event dropped from this instance's memory. */
  enum class Invalidation { ANSWERS, TILES, TICKETS }

  private val socketAuth = SocketAuth.entries.associateWith {
    counter("api.ws.auth", "First frames of sockets, by outcome", "result" to it.name.lowercase())
  }

  private val sendFailures = SendFailure.entries.associateWith {
    counter("api.ws.send.failures", "Frames that could not be sent to a socket", "reason" to it.name.lowercase())
  }

  private val invalidations = Invalidation.entries.associateWith {
    counter("api.fanout.invalidations", "Cache entries dropped because an event said they changed", "target" to it.name.lowercase())
  }

  // Every event type the zone can send, read off the sealed contract, so a new one is a series without a line here.
  private val events = ZoneEvent::class.sealedSubclasses.mapNotNull { it.simpleName }.associateWith {
    counter("api.fanout.events", "Zone events read from the stream and fanned out, by type", "type" to it)
  }

  private val keepAliveFailures = counter("api.ws.keepalive.failures", "Pings and expiry closes that failed")
  private val expired = counter("api.ws.expired", "Sockets closed because the token they opened with ran out")
  private val replayed = counter("api.ws.replayed", "Events replayed to a socket that reconnected with a last event id")
  private val frames = counter("api.ws.frames.sent", "Frames written to sockets, one per socket")

  fun socketAuth(result: SocketAuth) {
    socketAuth.getValue(result).increment()
  }

  fun sendFailed(reason: SendFailure) {
    sendFailures.getValue(reason).increment()
  }

  fun invalidated(target: Invalidation) {
    invalidations.getValue(target).increment()
  }

  fun event(type: String) {
    events[type]?.increment()
  }

  fun keepAliveFailed() {
    keepAliveFailures.increment()
  }

  fun expired() {
    expired.increment()
  }

  fun replayed(count: Int) {
    replayed.increment(count.toDouble())
  }

  fun frameSent() {
    frames.increment()
  }

  private fun counter(name: String, description: String, vararg tag: Pair<String, String>): Counter {
    val builder = Counter.builder(name).description(description)
    tag.forEach { (key, value) -> builder.tag(key, value) }
    return builder.register(registry)
  }
}
