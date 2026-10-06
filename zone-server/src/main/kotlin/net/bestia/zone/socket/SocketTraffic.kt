package net.bestia.zone.socket

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

/**
 * What all game connections together read, write and flush. No per-account tags: one series per player would
 * outgrow the metrics store. The default registry serves tests that build a pipeline by hand.
 */
@Component
class SocketTraffic(registry: MeterRegistry = SimpleMeterRegistry()) {

  private val written = Counter.builder("zone.socket.written")
    .baseUnit("bytes")
    .description("Bytes flushed to game clients")
    .register(registry)

  private val read = Counter.builder("zone.socket.read")
    .baseUnit("bytes")
    .description("Bytes received from game clients")
    .register(registry)

  private val flushes = Counter.builder("zone.socket.flushes")
    .description("Flushes to game clients; each is at least one write syscall")
    .register(registry)

  private val flushSize = DistributionSummary.builder("zone.socket.flush.size")
    .baseUnit("bytes")
    .description("Bytes per flush")
    .serviceLevelObjectives(1024.0, 4096.0, 16384.0, 65536.0, 262144.0)
    .register(registry)

  private val droppedForBacklog = dropCounter(registry, "backlog")
  private val droppedAsUnwritable = dropCounter(registry, "unwritable")

  private val open = AtomicInteger()

  init {
    Gauge.builder("zone.socket.channels", open) { it.get().toDouble() }
      .description("Open game connections, authenticated or not")
      .register(registry)
  }

  fun opened() {
    open.incrementAndGet()
  }

  fun closed() {
    open.decrementAndGet()
  }

  fun read(bytes: Int) {
    read.increment(bytes.toDouble())
  }

  fun flushed(bytes: Long) {
    flushes.increment()
    if (bytes == 0L) return

    written.increment(bytes.toDouble())
    flushSize.record(bytes.toDouble())
  }

  fun droppedForBacklog() {
    droppedForBacklog.increment()
  }

  fun droppedAsUnwritable() {
    droppedAsUnwritable.increment()
  }

  private fun dropCounter(registry: MeterRegistry, reason: String): Counter {
    return Counter.builder("zone.socket.dropped")
      .tag("reason", reason)
      .description("Clients closed because they stopped reading")
      .register(registry)
  }
}
