package net.bestia.zone.navigation

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.local.LocalPathfindingService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** What the local pathfinder counts about the searches it runs, refuses and fails. */
class NavigationMetricsTest {

  private val meters = SimpleMeterRegistry()
  private val metrics = NavigationMetrics(meters)

  private fun service(config: NavigationConfig, walkable: (Vec3L) -> Boolean = { true }): LocalPathfindingService {
    return LocalPathfindingService(TestNavigation.flatGround(walkable), config, metrics)
  }

  private fun searches(result: String): Double {
    return meters.get("zone.nav.local.searches").tag("result", result).counter().count()
  }

  @Test
  fun `a search that finds a path is counted and timed`() {
    service(NavigationConfig()).path(Vec3L(0, 0, 0), Vec3L(4, 0, 0))

    assertEquals(1.0, searches("found"))
    assertEquals(1L, meters.get("zone.nav.local.duration").timer().count())
  }

  @Test
  fun `a search the budget refused is counted and never ran`() {
    val sut = service(NavigationConfig(localPathfindsPerTick = 1))

    sut.path(Vec3L(0, 0, 0), Vec3L(4, 0, 0))
    sut.path(Vec3L(0, 0, 0), Vec3L(0, 4, 0))

    assertEquals(1.0, searches("found"))
    assertEquals(1.0, searches("refused_budget"))
    assertEquals(1L, meters.get("zone.nav.local.duration").timer().count(), "only one search ran")
  }

  @Test
  fun `a target beyond the local range is counted as too far`() {
    service(NavigationConfig(localSearchSpan = 16)).path(Vec3L(0, 0, 0), Vec3L(500, 0, 0))

    assertEquals(1.0, searches("too_far"))
    assertEquals(0L, meters.get("zone.nav.local.duration").timer().count())
  }

  @Test
  fun `a target on ground nobody can stand on is counted as unwalkable`() {
    service(NavigationConfig()) { it.x != 4L }.path(Vec3L(0, 0, 0), Vec3L(4, 0, 0))

    assertEquals(1.0, searches("goal_unwalkable"))
  }

  @Test
  fun `a sealed-off target is counted as no route`() {
    val sealed = { p: Vec3L -> !(Math.abs(p.x - 6) <= 1 && Math.abs(p.y) <= 1) || (p.x == 6L && p.y == 0L) }

    service(NavigationConfig(), sealed).path(Vec3L(0, 0, 0), Vec3L(6, 0, 0))

    assertEquals(1.0, searches("no_route"))
  }

  @Test
  fun `what a tick's searches cost is recorded when the budget resets`() {
    val sut = service(NavigationConfig(localPathfindsPerTick = 8))

    repeat(3) { sut.path(Vec3L(0, 0, 0), Vec3L(4, it.toLong(), 0)) }
    sut.resetBudget()
    sut.resetBudget()

    val spent = meters.get("zone.nav.local.budget.spent").summary()
    assertEquals(2L, spent.count())
    assertEquals(3.0, spent.totalAmount(), "three in the first tick, none in the second")
  }

  @Test
  fun `greedy steps are counted by whether they moved`() {
    val sut = service(NavigationConfig()) { it.x != 1L }

    sut.step(Vec3L(0, 0, 0), Vec3L(4, 0, 0))
    sut.step(Vec3L(0, 0, 0), Vec3L(0, 3, 0))

    assertEquals(1.0, meters.get("zone.nav.local.steps").tag("result", "blocked").counter().count())
    assertEquals(1.0, meters.get("zone.nav.local.steps").tag("result", "moved").counter().count())
  }

  @Test
  fun `a macro route that cannot be made is counted by why`() {
    val navigation = NavigationService(
      macroGraph = macroGraphWithoutAGraph(),
      local = service(NavigationConfig()),
      profiles = net.bestia.zone.navigation.profile.MovementProfileRegistry().apply { load() },
      config = NavigationConfig(),
      metrics = metrics,
    )

    navigation.planRoute(Vec3L(0, 0, 0), Vec3L(500, 0, 0), null)

    assertEquals(1.0, meters.get("zone.nav.macro.routes").tag("result", "no_graph").counter().count())
    assertEquals(0.0, meters.get("zone.nav.macro.routes").tag("result", "planned").counter().count())
  }

  private fun macroGraphWithoutAGraph(): net.bestia.zone.navigation.graph.MacroGraphService {
    return net.bestia.zone.navigation.graph.MacroGraphService(
      TestNavigation.noMacroGraph(), TestNavigation.flatGround(), NavigationConfig(),
    )
  }
}
