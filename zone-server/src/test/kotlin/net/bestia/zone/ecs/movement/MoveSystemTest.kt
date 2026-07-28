package net.bestia.zone.ecs.movement

import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoveSystemTest {

  /** A ridge running along x: height 100 at x=0 rising to 104 at x=4, so a straight line cannot follow it. */
  private val ridge = GroundHeight { x, _ -> 100L + x }

  /** Walks the whole path and returns where the entity ended up. */
  private fun walk(path: List<Vec3L>, ground: GroundHeight): Position {
    val world = testWorld(systems = listOf(MoveSystem(ground)))
    val id = world.create()

    val position = Position(0, 0, 100)
    world.add(id, position)
    world.add(id, Speed(1.0f))
    world.add(id, Path(path.toMutableList()))

    // Ticked until the path is drained rather than a counted number of times: `MoveSystem` advances on
    // `fraction > 1` strictly, so one tile per tick of unit speed is always one short and a count here would be
    // asserting the rollover arithmetic rather than the height.
    repeat(path.size * 2 + 2) { world.tick(1.0f) }

    return position
  }

  @Test
  fun `the ground decides the height, not the path the client sent`() {
    // What the client actually sends: path_calculator.gd lerps the vertical between the endpoints and says in
    // its own docstring that it ignores terrain. Over a rise that means the character cuts through the slope.
    // These waypoints are that straight line - flat at 100 across ground that climbs to 104.
    val flatLine = listOf(
      Vec3L(1, 0, 100),
      Vec3L(2, 0, 100),
      Vec3L(3, 0, 100),
      Vec3L(4, 0, 100)
    )

    val position = walk(flatLine, ridge)

    assertEquals(4, position.x)
    assertEquals(104, position.z, "walked the ridge, so the height must be the ground's and not the path's")
  }

  @Test
  fun `the whole path is corrected, not only the position, because observers render the path`() {
    // Path is synced to every client in range and entity.gd interpolates along it between position updates, so a
    // path left at the client's straight line makes observers draw the walk through the hill even though the
    // authoritative position is right.
    val world = testWorld(systems = listOf(MoveSystem(ridge)))
    val id = world.create()

    world.add(id, Position(0, 0, 100))
    world.add(id, Speed(1.0f))
    val path = Path(mutableListOf(Vec3L(1, 0, 100), Vec3L(2, 0, 100), Vec3L(3, 0, 100)))
    world.add(id, path)

    world.tick(0.1f)

    assertEquals(listOf(101L, 102L, 103L), path.path.map { it.z }, "the ridge climbs one per step")
    assertTrue(path.groundResolved)
  }

  @Test
  fun `a column with no height falls back to the waypoint rather than dropping the entity`() {
    // Off the grid, or before the world is generated. Refusing to move would be worse than trusting the
    // waypoint, and answering zero would drop the entity to sea level from wherever it was.
    val unknown = GroundHeight { _, _ -> null }

    val position = walk(listOf(Vec3L(1, 0, 137)), unknown)

    assertEquals(1, position.x)
    assertEquals(137, position.z)
  }
}
