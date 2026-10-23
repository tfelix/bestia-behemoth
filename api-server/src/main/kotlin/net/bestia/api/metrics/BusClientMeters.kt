package net.bestia.api.metrics

import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import net.bestia.bus.contract.ZoneEvent
import net.bestia.bus.valkey.ValkeyNodeBus
import net.bestia.bus.valkey.ValkeyStreamReader

/**
 * The bus client's plain counters, read at scrape time. The bus module has no Micrometer, so its classes count and
 * this reads; the timing and the per-query tags live in `ZoneAskMetrics`.
 */
class BusClientMeters(
  private val bus: ValkeyNodeBus,
  private val events: ValkeyStreamReader<ZoneEvent>,
) : MeterBinder {

  override fun bindTo(registry: MeterRegistry) {
    Gauge.builder("api.bus.pending", bus) { it.pendingCount.toDouble() }
      .description("Requests sent to the zone and not yet answered")
      .register(registry)
    counter(registry, "api.bus.asks", "Requests sent to the zone") { bus.askCount }
    counter(registry, "api.bus.ask.timeouts", "Requests whose caller gave up before a reply") { bus.timeoutCount }
    counter(registry, "api.bus.ask.unavailable", "Requests no zone was listening for") { bus.unavailableCount }
    counter(registry, "api.bus.publish.errors", "Requests the publish itself failed for") { bus.publishErrorCount }
    counter(registry, "api.bus.replies.dropped", "Replies that could not be used", "reason" to "unreadable") { bus.unreadableReplyCount }
    counter(registry, "api.bus.replies.dropped", "Replies that could not be used", "reason" to "orphan") { bus.orphanReplyCount }

    counter(registry, "api.bus.stream.delivered", "Stream entries handed to a listener", "stream" to STREAM) { events.deliveredCount }
    counter(registry, "api.bus.stream.failures", "Stream entries or reads that failed", "stream" to STREAM, "kind" to "listener") { events.listenerFailureCount }
    counter(registry, "api.bus.stream.failures", "Stream entries or reads that failed", "stream" to STREAM, "kind" to "decode") { events.decodeFailureCount }
    counter(registry, "api.bus.stream.failures", "Stream entries or reads that failed", "stream" to STREAM, "kind" to "read") { events.readFailureCount }
  }

  private fun counter(registry: MeterRegistry, name: String, description: String, vararg tag: Pair<String, String>, read: () -> Long) {
    val builder = FunctionCounter.builder(name, this) { read().toDouble() }.description(description)
    tag.forEach { (key, value) -> builder.tag(key, value) }
    builder.register(registry)
  }

  private companion object {
    const val STREAM = "events"
  }
}
