package net.bestia.zone.metrics

import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.FunctionTimer
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.SystemStats
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * What each system costs over time. Bound once the context has started, because systems are registered after
 * every singleton exists and a `MeterBinder` may run before that.
 */
@Component
class SystemMeters(
  private val registry: MeterRegistry,
  private val world: EcsWorld,
) {

  @EventListener(ApplicationStartedEvent::class)
  fun bind() {
    val stats = world.systemStats()

    for (system in stats) {
      FunctionTimer.builder(
        "zone.system.duration", system,
        { it.runs },
        { it.totalNanos.toDouble() },
        TimeUnit.NANOSECONDS
      )
        .tags("system", system.name, "phase", system.phase.name.lowercase())
        .description("Time one system spent in its updates")
        .register(registry)
      FunctionCounter.builder("zone.system.failures", system) { it.failures.toDouble() }
        .tag("system", system.name)
        .description("Updates of one system that threw")
        .register(registry)
    }

    Gauge.builder("zone.systems.disabled", stats) { all -> all.count(SystemStats::disabled).toDouble() }
      .description("Systems switched off after failing too often in a row")
      .strongReference(true)
      .register(registry)
  }
}
