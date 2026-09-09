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

/**
 * What a wandering creature's territory actually is.
 *
 * These pin the two properties that used to be entangled: how far a creature strays from home (the leash)
 * and how far it walks in one go (the stride). The draw used to come off the leash, so a home range worth
 * the name produced strides far longer than local pathfinding is sized for.
 *
 * Every assertion runs over many bouts because the target is drawn at random - a single sample would pass
 * against a broken clamp roughly seven times in eight.
 */
class LocomotionWanderTest {

  private val sut = Locomotion(TestNavigation.service())

  @Test
  fun `wanderStep never walks further than the stride, whatever the territory`() {
    val home = Vec3L(0, 0, 0)

    // Per axis, which is what the code guarantees and what a path actually costs: a diagonal bout of five
    // tiles is five steps, though Euclid calls it seven.
    forEachBout(home, radius = 400L) { from, to ->
      val steps = maxOf(kotlin.math.abs(to.x - from.x), kotlin.math.abs(to.y - from.y))
      assertTrue(
        steps <= Locomotion.WANDER_STEP_TILES,
        "a bout from $from to $to is $steps steps, past ${Locomotion.WANDER_STEP_TILES}"
      )
    }
  }

  @Test
  fun `wanderStep keeps the creature inside its radius`() {
    val home = Vec3L(0, 0, 0)
    val radius = 30L

    forEachBout(home, radius) { _, to ->
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
  fun `wanderStep never places the creature at a box corner`() {
    val home = Vec3L(0, 0, 0)
    val radius = 12L

    var sawFar = false
    forEachBout(home, radius, bouts = 4_000) { _, to ->
      if (to.distance(home) > radius) sawFar = true
    }

    assertTrue(!sawFar, "a creature reached past its radius, so the clamp is still a box")
  }

  @Test
  fun `wanderStep brings a creature that starts outside its radius back towards home`() {
    val home = Vec3L(0, 0, 0)
    val radius = 20L
    val world = testWorld()
    val id = spawnAt(world, Vec3L(60, 60, 0))

    // Only the candidates that land inside the disc survive, so the one step it takes must close the gap.
    val before = position(world, id).distance(home)
    sut.wanderStep(context(world, id), home, radius)
    arrive(world, id)

    assertTrue(
      position(world, id).distance(home) < before,
      "the creature moved from $before tiles away to ${position(world, id).distance(home)}"
    )
  }

  /**
   * Drives [bouts] complete wander bouts and hands each leg to [assertion].
   *
   * A bout is only started when the creature is standing still, so the path from the previous one is
   * consumed first - `wanderStep` returns early while a `Path` is present.
   */
  private fun forEachBout(home: Vec3L, radius: Long, bouts: Int = 2_000, assertion: (Vec3L, Vec3L) -> Unit) {
    val world = testWorld()
    val id = spawnAt(world, home)

    repeat(bouts) {
      val from = position(world, id)
      if (!sut.wanderStep(context(world, id), home, radius)) return@repeat
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
