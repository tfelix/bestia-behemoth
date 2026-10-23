package net.bestia.zone.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.mockk.mockk
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.message.TickOutbox
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TickMetersTest {

  private class SleepingSystem : System {
    override val phase = Phase.UPKEEP

    override fun update(world: World, deltaTime: Float) {
      Thread.sleep(3)
    }
  }

  private val world = testWorld(systems = listOf(SleepingSystem()))
  private val meters = SimpleMeterRegistry()

  private fun bind() {
    TickMeters(world, TickOutbox(mockk(relaxed = true))).bindTo(meters)
  }

  @Test
  fun `a tick's stages and phases are read from the totals the world keeps`() {
    bind()

    world.tick(0.05f)

    val systems = meters.get("zone.tick.stage.duration").tag("stage", "systems").functionTimer()
    assertEquals(1.0, systems.count())
    assertTrue(systems.totalTime(TimeUnit.MILLISECONDS) >= 3.0, "the system slept 3 ms")
    assertTrue(meters.get("zone.tick.phase.duration").tag("phase", "upkeep").functionTimer().totalTime(TimeUnit.MILLISECONDS) >= 3.0)
    assertEquals(0.0, meters.get("zone.tick.phase.duration").tag("phase", "ai").functionTimer().totalTime(TimeUnit.NANOSECONDS))
  }

  @Test
  fun `posted work is counted in its lane`() {
    bind()
    world.post { }

    world.tick(0.05f)

    assertEquals(1.0, meters.get("zone.world.posted.duration").tag("lane", "tick_start").functionTimer().count())
    assertEquals(0.0, meters.get("zone.world.posted.duration").tag("lane", "between_steps").functionTimer().count())
  }

  @Test
  fun `every failure the zone survives has a series that starts at zero`() {
    bind()

    val kinds = listOf("deferred_failure", "posted_failure", "lease_timeout", "query_skip", "outbox_failure")
    kinds.forEach { kind ->
      assertTrue(meters.get("zone.anomalies").tag("kind", kind).functionCounter().count() >= 0.0, kind)
    }
  }

  @Test
  fun `a deferred change that throws is counted`() {
    val failing = testWorld(systems = listOf(DeferringSystem()))
    TickMeters(failing, TickOutbox(mockk(relaxed = true))).bindTo(meters)

    failing.tick(0.05f)

    assertEquals(1.0, meters.get("zone.anomalies").tag("kind", "deferred_failure").functionCounter().count())
  }

  /** Defers a change that throws, which only counts while the systems are iterating. */
  private class DeferringSystem : System {
    override val phase = Phase.UPKEEP

    override fun update(world: World, deltaTime: Float) {
      world.defer { error("boom") }
    }
  }

  @Test
  fun `the series carry the names the dashboards query`() {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    TickMeters(world, TickOutbox(mockk(relaxed = true))).bindTo(prometheus)
    SystemMeters(prometheus, world).bind()
    world.tick(0.05f)

    val scrape = prometheus.scrape()
    listOf(
      "zone_tick_stage_duration_seconds_count",
      "zone_tick_phase_duration_seconds_sum",
      "zone_world_posted_duration_seconds_count",
      "zone_world_lease_duration_seconds_count",
      "zone_anomalies_total",
      "zone_system_duration_max_seconds",
    ).forEach { assertTrue(scrape.contains(it), "$it is missing from the scrape") }
  }
}
