package net.bestia.zone.ground

import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.OffRoad
import net.bestia.zone.movement.ecs.Position
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OffRoadSystemTest {

  private var made: Boolean? = false
  private val system = OffRoadSystem { made }
  private val world = testWorld()

  private fun master(offRoadSpeedPercent: Int) = world.createEntity {
    world.add(it, Master(it))
    world.add(it, Position(0, 0, 0))
    world.add(it, CombatStats(CombatValues.ofBase(1, StatusValues(1, 1, 1, 1, 1, 1)).copy(offRoadSpeedPercent = offRoadSpeedPercent)))
  }

  @Test
  fun `a Trailblazer off the road is marked once, and the mark goes on the road`() {
    val hunter = master(offRoadSpeedPercent = 9)

    system.update(world, 0.5f)
    assertTrue(world.has(hunter, OffRoad::class))
    assertTrue(world.has(hunter, IsStatusValueDirty::class))

    world.remove(hunter, IsStatusValueDirty::class)
    system.update(world, 0.5f)
    assertFalse(world.has(hunter, IsStatusValueDirty::class), "still off the road is no news")

    made = true
    system.update(world, 0.5f)
    assertFalse(world.has(hunter, OffRoad::class))
    assertTrue(world.has(hunter, IsStatusValueDirty::class))
  }

  @Test
  fun `ground nobody can read yet changes nothing`() {
    val hunter = master(offRoadSpeedPercent = 9)
    made = null

    system.update(world, 0.5f)

    assertFalse(world.has(hunter, OffRoad::class))
  }

  @Test
  fun `a master without Trailblazer is never marked`() {
    val walker = master(offRoadSpeedPercent = 0)

    system.update(world, 0.5f)

    assertFalse(world.has(walker, OffRoad::class))
  }
}
