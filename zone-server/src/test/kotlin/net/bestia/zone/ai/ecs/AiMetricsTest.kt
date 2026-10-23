package net.bestia.zone.ai.ecs

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AiMetricsTest {

  private val meters = SimpleMeterRegistry()
  private val metrics = AiMetrics(meters)

  @Test
  fun `a failed action is counted under its own name`() {
    metrics.actionFailed("attack")
    metrics.actionFailed("attack")
    metrics.actionFailed("flee")

    assertEquals(2.0, meters.get("zone.ai.actions.failed").tag("action", "attack").counter().count())
    assertEquals(1.0, meters.get("zone.ai.actions.failed").tag("action", "flee").counter().count())
  }

  @Test
  fun `a plan that replaces a running one is a preemption`() {
    metrics.adopted("Eat", preempted = false)
    metrics.adopted("Flee", preempted = true)

    assertEquals(1.0, meters.get("zone.ai.goals.adopted").tag("goal", "Flee").counter().count())
    assertEquals(1.0, meters.get("zone.ai.goals.preempted").counter().count())
  }

  @Test
  fun `the agents of a sweep are published by detail and a level nobody is at reads zero`() {
    metrics.agents(mapOf(AiDetail.FULL to 3, AiDetail.BACKGROUND to 100))

    assertEquals(3.0, meters.get("zone.ai.agents").tag("detail", "full").gauge().value())
    assertEquals(0.0, meters.get("zone.ai.agents").tag("detail", "reduced").gauge().value())
    assertEquals(100.0, meters.get("zone.ai.agents").tag("detail", "background").gauge().value())
  }

  @Test
  fun `a search is counted by whether it found a plan, with the iterations it took`() {
    metrics.planned(spent = 250, found = true)
    metrics.planned(spent = 10_000, found = false)

    assertEquals(1.0, meters.get("zone.ai.plan.searches").tag("result", "none").counter().count())
    assertEquals(2L, meters.get("zone.ai.plan.iterations").summary().count())
    assertEquals(10_250.0, meters.get("zone.ai.plan.iterations").summary().totalAmount())
  }
}
