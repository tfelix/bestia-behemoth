package net.bestia.zone.persistence

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * How far behind the database the world is. An entity is saved by a snapshot on the tick and a write on a DB
 * worker, so the world can be minutes ahead of its rows without anything failing; a crash loses the difference.
 * These meters say how much is in flight, how old the oldest of it is, and what was dropped.
 *
 * Counted per entity, not per job, because a job carries a whole batch. Recorded from the tick and from the
 * DB workers; Micrometer's meters are safe for both. The default registry serves tests that build the parts by
 * hand.
 */
@Component
class PersistenceMetrics(private val registry: MeterRegistry = SimpleMeterRegistry()) {

  /** One write job on its way to the database. */
  class Pending internal constructor(val entities: Int, internal val queuedAt: Long = System.nanoTime())

  private val queuedEntities = entities("queued", "Entities whose snapshot was queued for a write")
  private val unchangedEntities = entities("unchanged", "Entities a periodic save skipped because nothing changed since the last")
  private val writtenEntities = entities("written", "Entities whose write reached the database")
  private val failedEntities = entities("failed", "Entities whose write threw; the next save writes them again")
  private val rejectedEntities = entities("rejected", "Entities whose write was dropped because the worker's queue was full; the next save writes them again")

  private val writeDuration = Timer.builder("zone.persist.write.duration")
    .description("Time a DB worker spent on one write job, a batch of entities with one owner")
    .register(registry)

  private val writeRows = DistributionSummary.builder("zone.persist.write.batch")
    .description("Entities in one write job")
    .register(registry)

  private val leaving = Counter.builder("zone.persist.leaving")
    .description("Entities saved because they were leaving the world (a player logging out)")
    .register(registry)

  private val sweepDuration = Timer.builder("zone.persist.sweep.duration")
    .description("Time the tick spent taking snapshots for one sweep; the writes themselves run on DB workers")
    .register(registry)

  private val sweepDue = Counter.builder("zone.persist.sweep.due")
    .description("Entities that came due in a sweep and were looked at")
    .register(registry)

  private val deletions = listOf("submitted", "rejected").associateWith { result ->
    Counter.builder("zone.persist.deletions")
      .tag("result", result)
      .description("Rows of removed entities queued for deletion; rejected ones go back on the queue")
      .register(registry)
  }

  private val inFlight = AtomicInteger()
  private val inFlightJobs = ConcurrentHashMap.newKeySet<Pending>()

  init {
    Gauge.builder("zone.persist.pending", inFlight) { it.get().toDouble() }
      .description("Entities queued for a write that has not finished")
      .register(registry)
    Gauge.builder("zone.persist.oldest.age", inFlightJobs) { jobs -> ageOf(jobs) }
      .baseUnit("seconds")
      .description("How long the oldest write not yet finished has been waiting; 0 when none is")
      .register(registry)
  }

  /** [entities] are about to be queued as one write job; hand the result back to [rejected] or [finished]. */
  fun queued(entities: Int): Pending {
    val pending = Pending(entities)
    inFlight.addAndGet(entities)
    inFlightJobs.add(pending)
    queuedEntities.increment(entities.toDouble())
    writeRows.record(entities.toDouble())

    return pending
  }

  /** The job never got a worker: its queue was full. */
  fun rejected(pending: Pending) {
    settle(pending)
    rejectedEntities.increment(pending.entities.toDouble())
  }

  /** The job ran, [ok] or not, in [runNanos]. */
  fun finished(pending: Pending, ok: Boolean, runNanos: Long) {
    settle(pending)
    writeDuration.record(runNanos, TimeUnit.NANOSECONDS)
    (if (ok) writtenEntities else failedEntities).increment(pending.entities.toDouble())
  }

  fun unchanged() {
    unchangedEntities.increment()
  }

  fun leaving(entities: Int) {
    leaving.increment(entities.toDouble())
  }

  /** Times one sweep's snapshots, and counts the [due] entities it looked at. */
  fun <T> swept(due: () -> Int, block: () -> T): T {
    val started = System.nanoTime()
    try {
      return block()
    } finally {
      sweepDuration.record(System.nanoTime() - started, TimeUnit.NANOSECONDS)
      sweepDue.increment(due().toDouble())
    }
  }

  fun deletions(rows: Int, submitted: Boolean) {
    deletions.getValue(if (submitted) "submitted" else "rejected").increment(rows.toDouble())
  }

  private fun settle(pending: Pending) {
    if (inFlightJobs.remove(pending)) inFlight.addAndGet(-pending.entities)
  }

  private fun ageOf(jobs: Collection<Pending>): Double {
    val oldest = jobs.minOfOrNull { it.queuedAt } ?: return 0.0

    return (System.nanoTime() - oldest) / 1_000_000_000.0
  }

  private fun entities(outcome: String, description: String): Counter {
    return Counter.builder("zone.persist.entities")
      .tag("outcome", outcome)
      .description(description)
      .register(registry)
  }
}
