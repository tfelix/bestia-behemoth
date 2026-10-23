package net.bestia.zone.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import net.bestia.zone.persistence.PersistedEntityDeletionQueue
import org.springframework.stereotype.Component

/** The persistence queues that keep their own count. What was written and what it cost is `PersistenceMetrics`. */
@Component
class PersistenceMeters(private val deletions: PersistedEntityDeletionQueue) : MeterBinder {

  override fun bindTo(registry: MeterRegistry) {
    Gauge.builder("zone.persist.deletions.pending", deletions) { it.pending.toDouble() }
      .description("Removed entities whose rows have not been queued for deletion yet; drained once a second")
      .register(registry)
  }
}
