package net.bestia.zone.ecs.movement

import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoveSystemTest {

  /** A ridge running along x: height 100 at x=0 rising to 104 at x=4, so a straight line cannot follow it. */
  private val ridge = GroundHeight { position -> 100L + position.x }

  /** Level ground, for the tests that are about what goes on the wire rather than about height. */
  private val flat = GroundHeight { 100L }

  /** A straight walk east along [flat], one waypoint per tile. */
  private fun straightPath(tiles: Int) = MutableList(tiles) { i -> Vec3L(i + 1L, 0, 100) }

  private class Walker(
    val world: World,
    val id: EntityId,
    val position: Position,
    val path: Path
  )

  /** An entity at the origin with [path] ahead of it, ready to be ticked. */
  private fun walker(path: List<Vec3L>, speed: Float, ground: GroundHeight = flat): Walker {
    val world = testWorld(systems = listOf(MoveSystem(ground)))
    val id = world.create()

    val position = Position(0, 0, 100)
    val pathComponent = Path(path.toMutableList())
    world.add(id, position)
    world.add(id, Speed(speed))
    world.add(id, pathComponent)

    return Walker(world, id, position, pathComponent)
  }

  /** Walks [path] to its end, one tile per tick, and returns where the entity ended up. */
  private fun walk(path: List<Vec3L>, ground: GroundHeight): Position {
    val walker = walker(path, speed = 1.0f, ground = ground)

    repeat(path.size) { walker.world.tick(1.0f) }

    return walker.position
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
    val walker = walker(
      listOf(Vec3L(1, 0, 100), Vec3L(2, 0, 100), Vec3L(3, 0, 100)),
      speed = 1.0f,
      ground = ridge
    )

    walker.world.tick(0.1f)

    assertEquals(listOf(101L, 102L, 103L), walker.path.path.map { it.z }, "the ridge climbs one per step")
    assertTrue(walker.path.groundResolved)
  }

  @Test
  fun `walking a path re-sends neither the path nor the position`() {
    // Both used to go out on every tile step - four a second per moving entity per observer - and both
    // said only what the client can already work out from the waypoints it has and the speed. See the
    // notes on Path and Position for what that cost and what it broke.
    val world = testWorld(systems = listOf(MoveSystem(flat)))
    val id = world.create()

    val position = Position(0, 0, 100)
    val path = Path(straightPath(4))
    world.add(id, position)
    world.add(id, Speed(1.0f))
    world.add(id, path)

    // The first tick is the one send observers get: the path arrives dirty, and the ground resolves on it.
    world.tick(1.0f)
    assertTrue(path.isDirty(), "the walk itself has to be announced")
    path.clearDirty()
    position.clearDirty()

    world.tick(1.0f)

    assertEquals(2L, position.x, "the entity did step")
    assertFalse(path.isDirty(), "the shrinking remainder is not news")
    assertFalse(position.isDirty(), "nor is a step the client predicts for itself")
  }

  @Test
  fun `a step is indexed even when it is not published`() {
    // The area-of-interest index is not the wire: it answers who receives a broadcast, who an area
    // effect hits and what a skill can target, so it has to follow every step. See Position.moved.
    val world = testWorld(systems = listOf(MoveSystem(flat)))
    val id = world.create()

    val position = Position(0, 0, 100)
    world.add(id, position)
    world.add(id, Speed(1.0f))
    world.add(id, Path(straightPath(4)))

    world.tick(1.0f)
    position.clearDirty()
    position.clearMoved()

    world.tick(1.0f)

    assertTrue(position.moved, "every step has to reach the index")
    assertFalse(position.isDirty(), "but not the client")
  }

  @Test
  fun `one step in POSITION_RESYNC_STEPS is published as a resync`() {
    val world = testWorld(systems = listOf(MoveSystem(flat)))
    val id = world.create()

    val tiles = MoveSystem.POSITION_RESYNC_STEPS + 2
    val position = Position(0, 0, 100)
    world.add(id, position)
    world.add(id, Speed(1.0f))
    world.add(id, Path(straightPath(tiles)))
    position.clearDirty()   // the spawn push, not part of the walk

    var publishes = 0
    repeat(tiles + 2) {
      world.tick(1.0f)
      if (position.isDirty()) {
        publishes++
        position.clearDirty()
      }
    }

    assertEquals(tiles.toLong(), position.x, "the whole path was walked")
    assertEquals(1, publishes, "one resync in $tiles tiles, and the arrival rides on the stop instead")
  }

  @Test
  fun `the path carries how far past its last tile the entity stands`() {
    // Without it a client told about a walk already under way starts it a whole tile behind, because the
    // waypoints say where the walk goes and the position says only which tile was last reached.
    val world = testWorld(systems = listOf(MoveSystem(flat)))
    val id = world.create()

    val position = Position(0, 0, 100)
    val path = Path(straightPath(4))
    world.add(id, position)
    world.add(id, Speed(1.0f))
    world.add(id, path)

    world.tick(0.25f)

    assertEquals(0.25f, path.startOffset, 0.0001f, "a quarter of a tile at unit speed")
    assertEquals(0.25f, (path.toEntityMessage(id) as PathSMSG).startOffset, 0.0001f)
  }

  @Test
  fun `a column with no height falls back to the waypoint rather than dropping the entity`() {
    // Off the grid, or before the world is generated. Refusing to move would be worse than trusting the
    // waypoint, and answering zero would drop the entity to sea level from wherever it was.
    val unknown = GroundHeight { null }

    val position = walk(listOf(Vec3L(1, 0, 137)), unknown)

    assertEquals(1, position.x)
    assertEquals(137, position.z)
  }

  @Test
  fun `a tick long enough to drain the path lands on the last waypoint instead of throwing`() {
    // The removal is deferred to the end of the tick, so a path drained mid-loop is still attached on the next
    // turn of the rollover - which used to call removeFirst on an empty list and abandon every later wave plus
    // the tick's whole component sync.
    val walker = walker(listOf(Vec3L(1, 0, 100), Vec3L(2, 0, 100)), speed = 4.0f)

    // A second of travel at four tiles a second, against a path two tiles long.
    walker.world.tick(1.0f)

    assertEquals(2, walker.position.x)
    assertFalse(walker.world.has(walker.id, Path::class), "the path is spent, so it is gone")
    assertEquals(0f, walker.position.fraction, 1e-4f, "and its leftover travel does not carry to the next path")
  }

  @Test
  fun `one tile of travel is one tile, not one tick short of it`() {
    val walker = walker(listOf(Vec3L(1, 0, 100), Vec3L(2, 0, 100)), speed = 1.0f)

    walker.world.tick(1.0f)

    assertEquals(1, walker.position.x)
  }

  @Test
  fun `walking a path does not re-dirty it, because the client already has the waypoints`() {
    val walker = walker(listOf(Vec3L(1, 0, 100), Vec3L(2, 0, 100), Vec3L(3, 0, 100)), speed = 1.0f)

    // Resolving the waypoints against the terrain is a real change and does dirty it; what follows is the
    // walk itself.
    walker.world.tick(1.0f)
    walker.path.clearDirty()

    repeat(2) { walker.world.tick(1.0f) }

    assertFalse(walker.path.isDirty(), "stepping along a path the client already has is not news")
  }

  @Test
  fun `a fresh path starts from this tile, not from the previous walk's leftover progress`() {
    val walker = walker(listOf(Vec3L(1, 0, 100)), speed = 1.0f)

    // Nine tenths of the way into the first step, then re-routed.
    walker.world.tick(0.9f)
    walker.path.setPath(listOf(Vec3L(0, 1, 100)))
    walker.world.tick(0.5f)

    assertEquals(0, walker.position.y, "half a step into a fresh path is not a whole step")
  }
}
