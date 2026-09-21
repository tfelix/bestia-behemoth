package net.bestia.zone.ai.bt.leaves

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.TestNavigation
import net.bestia.zone.navigation.local.LocalWalkQuery
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Roaming is a leg and then a pause, and the pause is the part that matters.
 *
 * A creature that set off again the moment it arrived would put a `Path` and a stop message on the wire
 * several times a second, for every observer, and a zone full of them would spend the whole tick's
 * pathfinding budget between them - which is what made every NPC fall back to walking one tile at a time.
 */
class WanderTest {

  private val home = Vec3L(0, 0, 0)
  private val radius = 20L

  @Test
  fun `walks a leg, then stands still for the pause before the next one`() {
    val world = testWorld()
    val id = spawnAt(world, home)
    val leaf = Wander(home, locomotion(), radius, pauseSeconds = 2f..2f, random = Random(20260919L))

    leaf.tick(context(world, id))
    assertTrue(world.has(id, Path::class), "the first tick should set off")

    arrive(world, id)
    val pause = pauseBeforeNextLeg(leaf, world, id)

    assertTrue(pause >= 2f - DELTA, "stood still only $pause s, short of the 2 s pause")
    assertTrue(pause <= 2f + DELTA, "stood still $pause s, past the 2 s pause")
  }

  @Test
  fun `pauses three to five seconds by default`() {
    val world = testWorld()
    val id = spawnAt(world, home)
    val leaf = Wander(home, locomotion(), radius, random = Random(20260919L))

    leaf.tick(context(world, id))

    // Many legs rather than one, because the pause is drawn per leg and a single sample says nothing about
    // the range it was drawn from.
    repeat(40) {
      arrive(world, id)
      val pause = pauseBeforeNextLeg(leaf, world, id)

      assertTrue(
        pause >= Wander.DEFAULT_PAUSE_SECONDS.start - DELTA &&
            pause <= Wander.DEFAULT_PAUSE_SECONDS.endInclusive + DELTA,
        "stood still $pause s, outside ${Wander.DEFAULT_PAUSE_SECONDS}"
      )
    }
  }

  /**
   * Ground nothing can be pathed over, so every leg is refused.
   *
   * The refusal must cost what an ordinary leg costs - one attempt every few seconds - and not one attempt
   * per tick. Each search charges the zone's shared per-tick budget, so a creature hemmed in by terrain
   * would otherwise starve the ones that can still move.
   */
  @Test
  fun `a creature with nowhere to go pauses instead of searching every tick`() {
    var probes = 0
    val impassable = TestNavigation.flatGround { probes++; false }

    val world = testWorld()
    val id = spawnAt(world, home)
    val leaf = Wander(home, locomotion(impassable), radius, random = Random(20260919L))

    var searchingTicks = 0
    repeat(TEN_SECONDS_OF_TICKS) {
      val before = probes
      leaf.tick(context(world, id))
      if (probes > before) searchingTicks++
    }

    assertFalse(world.has(id, Path::class), "nowhere was walkable, so nothing should have been walked")
    // One on the first tick and one per pause of at least three seconds: four in ten seconds.
    assertTrue(searchingTicks <= 4, "asked the pathfinder on $searchingTicks separate ticks in ten seconds")
  }

  /**
   * Ticks until the leaf sets off again, and returns how long it stood still.
   *
   * Two of the ticks are the leaf noticing it arrived and the leaf setting off again; the rest is the pause.
   */
  private fun pauseBeforeNextLeg(leaf: Wander, world: World, id: EntityId): Float {
    var ticks = 0

    while (!world.has(id, Path::class)) {
      leaf.tick(context(world, id))
      ticks++

      if (ticks > TEN_SECONDS_OF_TICKS) return ticks * DELTA
    }

    return (ticks - 2) * DELTA
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
  }

  private fun locomotion(ground: LocalWalkQuery = TestNavigation.flatGround()): Locomotion {
    return Locomotion(TestNavigation.service(ground), Random(20260919L))
  }

  private fun spawnAt(world: World, at: Vec3L): EntityId {
    return world.createEntity { id -> add(id, Position.fromVec3(at)) }
  }

  private fun context(world: World, id: EntityId): BtContext {
    return BtContext(
      world = world,
      entityId = id,
      memory = Blackboard(),
      state = WorldState.EMPTY,
      deltaTime = DELTA,
      currentTick = 0L,
      tickRate = 20,
    )
  }

  private companion object {
    /** One tick at the zone's 20 Hz. */
    const val DELTA = 0.05f

    const val TEN_SECONDS_OF_TICKS = 200
  }
}
