package net.bestia.zone.casting

import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.ecs.effects.AreaMechanics
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroundWardsTest {

  private val world = testWorld()
  private val wards = GroundWards()

  init {
    world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(0, 0, 0)))
      add(
        id,
        AreaEffect(
          casterId = 1L, skillId = 68L, skillLevel = 1, radiusTiles = 3, damagePerTick = 0, tickIntervalSeconds = 1f,
          remainingTicks = 10, mechanics = AreaMechanics(blocksGroundSpells = true), potency = 60
        )
      )
    }
  }

  @Test
  fun `a much weaker ground spell never takes root inside the zone, a much stronger one always does`() {
    assertTrue(wards.blocks(world, Vec3L(2, 2, 0), potency = 30))
    assertFalse(wards.blocks(world, Vec3L(2, 2, 0), potency = 90))
  }

  @Test
  fun `outside the zone nothing is blocked`() {
    assertFalse(wards.blocks(world, Vec3L(10, 0, 0), potency = 1))
  }
}
