package net.bestia.zone.metrics

import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import net.bestia.zone.world.stream.ChunkStreamInbox
import net.bestia.zone.world.stream.ChunkWorkers
import org.springframework.stereotype.Component

/**
 * The parts of terrain streaming that keep their own counts on other threads: the workers that generate and
 * encode chunks, and the inbox the socket side drops requests from. What the tick spends is
 * `ChunkStreamMetrics`.
 */
@Component
class StreamingMeters(
  private val workers: ChunkWorkers,
  private val inbox: ChunkStreamInbox,
) : MeterBinder {

  override fun bindTo(registry: MeterRegistry) {
    Gauge.builder("zone.chunk.workers", workers) { it.queued.toDouble() }
      .tag("state", "queued")
      .description("Chunk workers: jobs waiting for a free worker, workers busy, results the tick has not taken yet")
      .register(registry)
    Gauge.builder("zone.chunk.workers", workers) { it.active.toDouble() }
      .tag("state", "active")
      .register(registry)
    Gauge.builder("zone.chunk.workers", workers) { it.undelivered.toDouble() }
      .tag("state", "undelivered")
      .register(registry)

    for (kind in listOf("requests", "carves", "teleports")) {
      FunctionCounter.builder("zone.chunk.inbox.dropped", inbox) { (it.dropped[kind] ?: 0L).toDouble() }
        .tag("kind", kind)
        .description("Work dropped because an inbox was over its cap: chunks for requests, requests for the rest")
        .register(registry)
    }
  }
}
