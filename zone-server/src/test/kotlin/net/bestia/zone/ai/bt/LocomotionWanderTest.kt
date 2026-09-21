package net.bestia.zone.ai.bt

import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.TestNavigation
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * What a wandering creature's territory actually is.
 *
 * These pin the two properties that must stay separate: how far a creature strays from home (the leash) and
 * how far it walks in one go (the leg). A draw taken off the leash would ask local pathfinding for searches
 * far longer than it is sized for.
 *
 * Every assertion runs over many legs because the target is drawn at random - a single sample would pass
 * against a broken clamp roughly seven times in eight.
 */
class LocomotionWanderTest {

  // Seeded rather than left to `Random.Default`: these assertions already run over many legs to catch a
  // broken clamp, and a seed means a run that *does* catch one can be replayed instead of described.
  private val sut = Locomotion(TestNavigation.service(), Random(20260914L))

  @Test
  fun `wanderLeg walks a whole leg and no further, whatever the territory`() {
    val home = Vec3L(0, 0, 0)

    // Per axis, which is what the code guarantees and what a path actually costs: a diagonal leg of four
    // tiles is four steps, though Euclid calls it five and a half.
    forEachLeg(home, radius = 400L) { from, to ->
      val steps = maxOf(kotlin.math.abs(to.x - from.x), kotlin.math.abs(to.y - from.y))
      assertTrue(
        steps in Locomotion.WANDER_LEG_MIN_TILES..Locomotion.WANDER_LEG_MAX_TILES,
        "a leg from $from to $to is $steps steps, outside " +
            "${Locomotion.WANDER_LEG_MIN_TILES}..${Locomotion.WANDER_LEG_MAX_TILES}"
      )
    }
  }

  @Test
  fun `wanderLeg keeps the creature inside its radius`() {
    val home = Vec3L(0, 0, 0)
    val radius = 30L

    forEachLeg(home, radius) { _, to ->
      assertTrue(to.distance(home) <= radius, "$to is ${to.distance(home)} tiles from home, past $radius")
    }
  }

  /**
   * The clamp is a disc, not a box.
   *
   * A per-axis clamp lets a creature stand at the corner, `radius * sqrt(2)` from home - which
   * `Goals.RETURN_HOME` would then consider out of range, putting it in a loop. Walking a long way from a
   * radius small enough that a box corner is reachable is what makes the difference visible.
   */
  @Test
  fun `wanderLeg never places the creature at a box corner`() {
    val home = Vec3L(0, 0, 0)
    val radius = 12L

    var sawFar = false
    forEachLeg(home, radius, legs = 4_000) { _, to ->
      if (to.distance(home) > radius) sawFar = true
    }

    assertTrue(!sawFar, "a creature reached past its radius, so the clamp is still a box")
  }

  @Test
  fun `wanderLeg brings a creature that starts outside its radius back towards home`() {
    val home = Vec3L(0, 0, 0)
    val radius = 20L
    val world = testWorld()
    val id = spawnAt(world, Vec3L(60, 60, 0))

    // Only the candidates that land inside the disc survive, so the one leg it takes must close the gap.
    val before = position(world, id).distance(home)
    sut.wanderLeg(context(world, id), home, radius)
    arrive(world, id)

    assertTrue(
      position(world, id).distance(home) < before,
      "the creature moved from $before tiles away to ${position(world, id).distance(home)}"
    )
  }

  /**
   * Drives [legs] complete wander legs and hands each one to [assertion].
   *
   * A leg is only started when the creature is standing still, so the path from the previous one is
   * consumed first - `wanderLeg` returns early while a `Path` is present.
   */
  private fun forEachLeg(home: Vec3L, radius: Long, legs: Int = 2_000, assertion: (Vec3L, Vec3L) -> Unit) {
    val world = testWorld()
    val id = spawnAt(world, home)

    repeat(legs) {
      val from = position(world, id)
      if (!sut.wanderLeg(context(world, id), home, radius)) return@repeat
      arrive(world, id)
      assertion(from, position(world, id))
    }
  }

  /** Teleports the entity to the end of its path, standing in for `MoveSystem` walking it there. */
  private fun arrive(world: World, id: EntityId) {
    val path = world.get(id, Path::class) ?: return
    val destination = path.path.last()
    val position = world.getOrThrow(id, Position::class)
    position.x = destination.x
    position.y = destination.y
    position.z = destination.z
    world.remove(id, Path::class)
    world.tick(0.05f)
  }

  private fun spawnAt(world: World, at: Vec3L): EntityId {
    return world.createEntity { id -> add(id, Position.fromVec3(at)) }
  }

  private fun position(world: World, id: EntityId): Vec3L {
    return world.getOrThrow(id, Position::class).toVec3L()
  }

  private fun context(world: World, id: EntityId): BtContext {
    return BtContext(
      world = world,
      entityId = id,
      memory = Blackboard(),
      state = WorldState.EMPTY,
      deltaTime = 0.05f,
      currentTick = 0L,
      tickRate = 20,
    )
  }
}
