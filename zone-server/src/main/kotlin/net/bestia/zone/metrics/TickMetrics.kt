package net.bestia.zone.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.message.EntitySMSG
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * What each tick cost and what its sync half sent, recorded by `ZoneEngine`. Buckets sit at half, one and two
 * tick budgets.
 */
@Component
class TickMetrics(private val registry: MeterRegistry, config: WorldRulesConfig) {

  private val budget = Duration.ofSeconds(1).dividedBy(config.tickRate.toLong())

  private val total = partTimer(registry, "total")
  private val systems = partTimer(registry, "systems")
  private val sync = partTimer(registry, "sync")

  private val overruns = Counter.builder("zone.tick.overruns")
    .description("Ticks that took longer than their budget")
    .register(registry)

  private val droppedSteps = Counter.builder("zone.tick.dropped.steps")
    .description("Ticks skipped because the loop fell too far behind to catch up")
    .register(registry)

  private val dirtyLive = dirtyCounter(registry, "live")
  private val dirtyStale = dirtyCounter(registry, "stale")

  private val syncEntities = syncCounter(registry, "entities", "Entities with a change to send")
  private val syncComponents = syncCounter(registry, "components", "Changed components sent, counted once however many receive them")
  private val syncMoved = syncCounter(registry, "moved", "Entities whose area-of-interest position was refreshed")
  private val syncAccounts = syncCounter(registry, "batches", "State batches sent: one per account with something to hear in a tick")
  private val syncUpdates = syncCounter(registry, "updates", "Entity updates inside those batches, counted once per receiver")
  private val syncSnapshots = syncCounter(registry, "snapshots", "Entities that came into someone's view and were sent whole")

  private val entityMessages = ConcurrentHashMap<KClass<out EntitySMSG>, Counter>()

  private val tickFailures = anomaly(registry, "tick_failure")
  private val postedLoopFailures = anomaly(registry, "posted_loop_failure")
  private val syncFailures = anomaly(registry, "sync_failure")

  fun recordParts(systemsNanos: Long, syncNanos: Long) {
    systems.record(systemsNanos, TimeUnit.NANOSECONDS)
    sync.record(syncNanos, TimeUnit.NANOSECONDS)
  }

  fun recordTotal(nanos: Long) {
    total.record(nanos, TimeUnit.NANOSECONDS)
    if (nanos > budget.toNanos()) overruns.increment()
  }

  fun stepsDropped(steps: Long) {
    if (steps > 0) droppedSteps.increment(steps.toDouble())
  }

  /** What one tick's sync half drained and sent; see `ZoneEngine.syncDirtyComponents`. */
  class SyncSample(
    val drained: Int,
    val stale: Int,
    val entities: Int,
    val components: Int,
    val moved: Int,
    val batches: Int,
    val updates: Int,
    val snapshots: Int,
  )

  fun recordSync(sample: SyncSample) {
    dirtyLive.increment((sample.drained - sample.stale).toDouble())
    dirtyStale.increment(sample.stale.toDouble())
    syncEntities.increment(sample.entities.toDouble())
    syncComponents.increment(sample.components.toDouble())
    syncMoved.increment(sample.moved.toDouble())
    syncAccounts.increment(sample.batches.toDouble())
    syncUpdates.increment(sample.updates.toDouble())
    syncSnapshots.increment(sample.snapshots.toDouble())
  }

  /**
   * [receivers] accounts are about to be told [message]. Counted per receiver, so the series add up to what the
   * clients hear. A series is made the first time a message class is sent, so it exists only once seen; the
   * classes are the entity messages the zone has, not anything a client chooses.
   */
  fun entityMessage(message: EntitySMSG, receivers: Int) {
    entityMessages.computeIfAbsent(message::class) { type ->
      Counter.builder("zone.sync.messages")
        .tag("type", type.simpleName ?: "unknown")
        .description("Entity messages sent to clients, counted once per receiver")
        .register(registry)
    }.increment(receivers.toDouble())
  }

  /**
   * Counts a failure kind whose total lives somewhere the metrics may not depend on, read when scraped. The owner
   * of the counter registers it, which is why this takes a function.
   */
  fun anomalySource(kind: String, count: () -> Long) {
    FunctionCounter.builder(ANOMALIES, this) { count().toDouble() }
      .tag("kind", kind)
      .description(ANOMALIES_DESCRIPTION)
      .register(registry)
  }

  /** A tick threw out of everything the systems and the sync contain. */
  fun tickFailed() {
    tickFailures.increment()
  }

  /** The posted work between two ticks threw something its own isolation did not catch. */
  fun postedLoopFailed() {
    postedLoopFailures.increment()
  }

  /** One entity's changes could not be turned into messages. */
  fun syncFailed() {
    syncFailures.increment()
  }

  private fun partTimer(registry: MeterRegistry, part: String): Timer {
    return Timer.builder("zone.tick.duration")
      .tag("part", part)
      .description("Time one tick took; systems and sync are its two halves")
      .serviceLevelObjectives(budget.dividedBy(2), budget, budget.multipliedBy(2))
      .register(registry)
  }

  private fun dirtyCounter(registry: MeterRegistry, outcome: String): Counter {
    return Counter.builder("zone.sync.dirty")
      .tag("outcome", outcome)
      .description("Dirty-log entries drained; a stale one has nothing left to send, which is normal")
      .register(registry)
  }

  private fun syncCounter(registry: MeterRegistry, what: String, description: String): Counter {
    return Counter.builder("zone.sync.$what")
      .description(description)
      .register(registry)
  }

  private fun anomaly(registry: MeterRegistry, kind: String): Counter {
    return Counter.builder(ANOMALIES)
      .tag("kind", kind)
      .description(ANOMALIES_DESCRIPTION)
      .register(registry)
  }

  companion object {
    const val ANOMALIES = "zone.anomalies"
    const val ANOMALIES_DESCRIPTION = "Failures the zone survives, and logs at most once a second: each is a bug"
  }
}
