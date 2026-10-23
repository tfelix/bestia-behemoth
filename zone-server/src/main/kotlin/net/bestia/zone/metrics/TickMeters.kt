package net.bestia.zone.metrics

import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.FunctionTimer
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Query
import net.bestia.zone.ecs.core.TickStage
import net.bestia.zone.message.TickOutbox
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Where a tick's time goes, read when scraped from totals the ECS keeps for itself: per stage of the tick, per
 * phase of the systems, the posted work around the tick, and the leases other threads borrow the world with.
 * Also the failures the zone survives, which are otherwise only a rate-limited log line.
 *
 * A tick's own timers are `TickMetrics`, the per-system ones `SystemMeters`.
 */
@Component
class TickMeters(
  private val world: EcsWorld,
  private val outbox: TickOutbox,
) : MeterBinder {

  override fun bindTo(registry: MeterRegistry) {
    bindStages(registry)
    bindPhases(registry)
    bindPostedWork(registry)
    bindLeases(registry)
    bindAnomalies(registry)
  }

  private fun bindStages(registry: MeterRegistry) {
    for (stage in TickStage.entries) {
      FunctionTimer.builder(
        "zone.tick.stage.duration", world,
        { it.tickCount },
        { it.stageNanos(stage).toDouble() },
        TimeUnit.NANOSECONDS
      )
        .tag("stage", stage.name.lowercase())
        .description("Time the three steps of a tick took: posted work, the systems, deferred changes")
        .register(registry)
    }
  }

  private fun bindPhases(registry: MeterRegistry) {
    for (phase in Phase.entries) {
      FunctionTimer.builder(
        "zone.tick.phase.duration", world,
        { it.tickCount },
        { it.phaseNanos(phase).toDouble() },
        TimeUnit.NANOSECONDS
      )
        .tag("phase", phase.name.lowercase())
        .description("Time the systems of one phase took; what the systems stage has beyond these is the scheduler's own")
        .register(registry)
    }
  }

  private fun bindPostedWork(registry: MeterRegistry) {
    val stats = world.ownershipStats

    postedLane(registry, "tick_start", { stats.tickStartTasks }, { stats.tickStartNanos })
    postedLane(registry, "between_steps", { stats.betweenStepsTasks }, { stats.betweenStepsNanos })
  }

  private fun postedLane(registry: MeterRegistry, lane: String, tasks: () -> Long, nanos: () -> Long) {
    FunctionTimer.builder(
      "zone.world.posted.duration", this,
      { tasks() },
      { nanos().toDouble() },
      TimeUnit.NANOSECONDS
    )
      .tag("lane", lane)
      .description("Posted work run on the tick thread, at the start of a tick and in the sleep between two")
      .register(registry)
  }

  private fun bindLeases(registry: MeterRegistry) {
    val stats = world.ownershipStats

    FunctionTimer.builder(
      "zone.world.lease.duration", stats,
      { it.leasesLent },
      { it.leaseLentNanos.toDouble() },
      TimeUnit.NANOSECONDS
    )
      .tag("side", "lent")
      .description("Leases of the world: how long the tick thread stood still while one was out (lent), how long a borrower waited for it (waited)")
      .register(registry)
    FunctionTimer.builder(
      "zone.world.lease.duration", stats,
      { it.leasesBorrowed },
      { it.leaseWaitNanos.toDouble() },
      TimeUnit.NANOSECONDS
    )
      .tag("side", "waited")
      .register(registry)
  }

  private fun bindAnomalies(registry: MeterRegistry) {
    anomaly(registry, "deferred_failure", world) { it.deferredFailures }
    anomaly(registry, "posted_failure", world.ownershipStats) { it.postedFailures }
    anomaly(registry, "lease_timeout", world.ownershipStats) { it.leaseTimeouts }
    anomaly(registry, "query_skip", Query.skippedEntities) { it.sum() }
    anomaly(registry, "outbox_failure", outbox) { it.failedBatches }
  }

  private fun <T : Any> anomaly(registry: MeterRegistry, kind: String, source: T, count: (T) -> Long) {
    FunctionCounter.builder(TickMetrics.ANOMALIES, source) { count(it).toDouble() }
      .tag("kind", kind)
      .description(TickMetrics.ANOMALIES_DESCRIPTION)
      .register(registry)
  }
}
