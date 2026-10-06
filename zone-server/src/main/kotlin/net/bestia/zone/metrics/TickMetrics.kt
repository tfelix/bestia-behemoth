package net.bestia.zone.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import net.bestia.zone.config.WorldRulesConfig
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

/** What each tick cost, recorded by `ZoneEngine`. Buckets sit at half, one and two tick budgets. */
@Component
class TickMetrics(registry: MeterRegistry, config: WorldRulesConfig) {

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

  private fun partTimer(registry: MeterRegistry, part: String): Timer {
    return Timer.builder("zone.tick.duration")
      .tag("part", part)
      .description("Time one tick took; systems and sync are its two halves")
      .serviceLevelObjectives(budget.dividedBy(2), budget, budget.multipliedBy(2))
      .register(registry)
  }
}
