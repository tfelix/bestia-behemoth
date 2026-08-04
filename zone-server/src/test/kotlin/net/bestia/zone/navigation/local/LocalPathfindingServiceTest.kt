package net.bestia.zone.navigation.local

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.NavigationConfig
import net.bestia.zone.navigation.TestNavigation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The tier that answers movement within sight, and the one that replaced a greedy step onto whatever tile
 * pointed at the target without checking it was walkable.
 */
class LocalPathfindingServiceTest {

  private fun service(
    config: NavigationConfig = NavigationConfig(),
    walkable: (Vec3L) -> Boolean = { true }
  ) = LocalPathfindingService(TestNavigation.flatGround(walkable), config)

  @Test
  fun `walks a straight line over open ground`() {
    val path = assertNotNull(service().path(Vec3L(0, 0, 0), Vec3L(4, 0, 0)))

    // The starting column is excluded: `Path` is a queue of places still to go.
    assertEquals(4, path.size)
    assertEquals(Vec3L(4, 0, 0), path.last())
    assertTrue(path.none { it == Vec3L(0, 0, 0) }, "the path should not include where the entity already is")
  }

  @Test
  fun `goes round a wall instead of through it`() {
    // The property the old greedy step could not have: a wall directly between start and goal. Greedy stepping
    // walked into it and stopped; this has to find the way round.
    val wall = { p: Vec3L -> !(p.x == 2L && p.y in -3..3) }
    val path = assertNotNull(service(walkable = wall).path(Vec3L(0, 0, 0), Vec3L(4, 0, 0)))

    assertEquals(Vec3L(4, 0, 0), path.last())
    assertTrue(path.none { it.x == 2L && it.y in -3..3 }, "the path walks through the wall: $path")
  }

  @Test
  fun `refuses a goal sealed off entirely`() {
    // A ring of impassable columns around the target. Without the expansion limit this is the search that
    // would expand every column in the box before giving up.
    val sealed = { p: Vec3L -> !(Math.abs(p.x - 6) <= 1 && Math.abs(p.y) <= 1) || (p.x == 6L && p.y == 0L) }

    assertNull(service(walkable = sealed).path(Vec3L(0, 0, 0), Vec3L(6, 0, 0)))
  }

  @Test
  fun `refuses a target beyond the local tier's range rather than searching for it`() {
    // Not a failure but a routing decision: that far away is the macro graph's job, and expanding thousands of
    // columns to discover so would waste the tick.
    val config = NavigationConfig(localSearchSpan = 16)

    assertNull(service(config).path(Vec3L(0, 0, 0), Vec3L(500, 0, 0)))
  }

  @Test
  fun `spends a bounded number of searches per tick and recovers after a reset`() {
    // The budget that keeps a whole pack running out of waypoints on one tick from starting a search each.
    val service = service(NavigationConfig(localPathfindsPerTick = 2))

    assertNotNull(service.path(Vec3L(0, 0, 0), Vec3L(3, 0, 0)))
    assertNotNull(service.path(Vec3L(0, 0, 0), Vec3L(4, 0, 0)))
    assertNull(service.path(Vec3L(0, 0, 0), Vec3L(5, 0, 0)), "the third search should exceed the budget")

    service.resetBudget()
    assertNotNull(service.path(Vec3L(0, 0, 0), Vec3L(5, 0, 0)), "a new tick should allow searching again")
  }

  @Test
  fun `a single step is checked for walkability rather than assumed`() {
    // What the greedy fallback does now, and the bug it fixes: the old version stepped onto whatever tile
    // pointed at the target without asking whether anything was there.
    val blockedEast = { p: Vec3L -> p.x <= 0L }
    val service = service(walkable = blockedEast)

    assertNull(service.step(Vec3L(0, 0, 0), Vec3L(5, 0, 0)), "stepping into a wall must be refused")
  }

  @Test
  fun `a step sidesteps when the direct diagonal is blocked`() {
    // A creature wedged against a corner used to stand still. One of the two axis-aligned alternatives should
    // still make progress.
    val noDiagonal = { p: Vec3L -> !(p.x == 1L && p.y == 1L) }
    val step = assertNotNull(service(walkable = noDiagonal).step(Vec3L(0, 0, 0), Vec3L(5, 5, 0)))

    assertTrue(
      step == Vec3L(1, 0, 0) || step == Vec3L(0, 1, 0),
      "expected a sidestep, got $step"
    )
  }

  @Test
  fun `a step onto the entity's own column is not movement`() {
    assertNull(service().step(Vec3L(3, 3, 0), Vec3L(3, 3, 0)))
    assertNull(service().path(Vec3L(3, 3, 0), Vec3L(3, 3, 0)))
  }
}
