package net.bestia.zone.ecs.battle.effects

import io.mockk.mockk
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.battle.damage.Damage
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AreaEffectSystemTest {

  private val aoi = EntityAOIService()
  private val outMessageProcessor = mockk<OutMessageProcessor>(relaxed = true)
  private val sut = AreaEffectSystem(aoi, outMessageProcessor)
  private val world: World = testWorld(systems = listOf(sut))

  /** The world's AOI index is fed by ZoneEngine's dirty-position pass, which no test world runs. */
  private fun victimAt(position: Vec3L, dead: Boolean = false): EntityId {
    val id = world.createEntity { entityId ->
      add(entityId, Position.fromVec3(position))
      add(entityId, Health(current = 500, max = 500))
      if (dead) add(entityId, Dead)
    }
    aoi.setEntityPosition(id, position)
    return id
  }

  private fun emberAt(center: Vec3L, caster: EntityId = CASTER_ID): EntityId {
    val id = world.createEntity { entityId ->
      add(entityId, Position.fromVec3(center))
      add(
        entityId,
        AreaEffect.lasting(
          casterId = caster,
          skillId = SKILL_ID,
          skillLevel = 1,
          radiusTiles = 1,
          damagePerTick = DAMAGE_PER_TICK,
          tickIntervalSeconds = TICK_INTERVAL,
          durationSeconds = DURATION
        )
      )
    }
    aoi.setEntityPosition(id, center)
    return id
  }

  /** Damage is staged rather than applied, and drained by ReceivedDamageSystem, which is not under test. */
  private fun stagedDamage(id: EntityId): Int = world.get(id, Damage::class)?.total() ?: 0

  @Test
  fun `nine point six seconds at one point two per tick deals exactly eight ticks`() {
    val victim = victimAt(CENTER)
    emberAt(CENTER)

    // Tick at the real 20 Hz rate rather than in one jump, so the accumulator is exercised the way
    // the engine drives it.
    repeat(200) { world.tick(0.05f) }

    assertEquals(8 * DAMAGE_PER_TICK, stagedDamage(victim), "expected exactly eight ticks of damage")
  }

  @Test
  fun `a radius of one covers the three by three around the centre and nothing beyond`() {
    val inside = victimAt(Vec3L(CENTER.x + 1, CENTER.y - 1, CENTER.z))
    val outside = victimAt(Vec3L(CENTER.x + 2, CENTER.y, CENTER.z))
    emberAt(CENTER)

    world.tick(TICK_INTERVAL)

    assertEquals(DAMAGE_PER_TICK, stagedDamage(inside), "a tile diagonally adjacent to the centre is inside a 3x3")
    assertEquals(0, stagedDamage(outside), "two tiles out is beyond a 3x3")
  }

  @Test
  fun `the patch is destroyed once its last tick has landed`() {
    val victim = victimAt(CENTER)
    val patch = emberAt(CENTER)

    repeat(200) { world.tick(0.05f) }

    assertFalse(world.isAlive(patch), "the effect entity should be gone once it burnt out")
    assertEquals(8 * DAMAGE_PER_TICK, stagedDamage(victim))
  }

  @Test
  fun `a long frame delivers every tick it swallowed rather than dropping them`() {
    val victim = victimAt(CENTER)
    emberAt(CENTER)

    // One 5s stall covers four whole intervals.
    world.tick(5f)

    assertEquals(4 * DAMAGE_PER_TICK, stagedDamage(victim))
  }

  @Test
  fun `the dead and the health-less are skipped`() {
    val corpse = victimAt(CENTER, dead = true)
    val scenery = world.createEntity { entityId -> add(entityId, Position.fromVec3(CENTER)) }
      .also { aoi.setEntityPosition(it, CENTER) }
    emberAt(CENTER)

    world.tick(TICK_INTERVAL)

    assertEquals(0, stagedDamage(corpse))
    assertFalse(world.has(scenery, Damage::class), "an entity with no Health takes no damage")
  }

  @Test
  fun `fire on the ground burns the caster who is standing in it`() {
    val caster = victimAt(CENTER)
    emberAt(CENTER, caster = caster)

    world.tick(TICK_INTERVAL)

    assertEquals(DAMAGE_PER_TICK, stagedDamage(caster))
  }

  @Test
  fun `an effect that spares its caster leaves them alone but still hits everyone else`() {
    val caster = victimAt(CENTER)
    val bystander = victimAt(Vec3L(CENTER.x + 1, CENTER.y, CENTER.z))
    val patch = world.createEntity { entityId ->
      add(entityId, Position.fromVec3(CENTER))
      add(
        entityId,
        AreaEffect.lasting(
          casterId = caster,
          skillId = SKILL_ID,
          skillLevel = 1,
          radiusTiles = 1,
          damagePerTick = DAMAGE_PER_TICK,
          tickIntervalSeconds = TICK_INTERVAL,
          durationSeconds = DURATION,
          hitsCaster = false
        )
      )
    }
    aoi.setEntityPosition(patch, CENTER)

    world.tick(TICK_INTERVAL)

    assertEquals(0, stagedDamage(caster))
    assertEquals(DAMAGE_PER_TICK, stagedDamage(bystander))
  }

  @Test
  fun `two patches on one target both stage their damage instead of one replacing the other`() {
    val victim = victimAt(CENTER)
    emberAt(CENTER)
    emberAt(CENTER)

    world.tick(TICK_INTERVAL)

    assertEquals(2 * DAMAGE_PER_TICK, stagedDamage(victim))
  }

  @Test
  fun `a patch never damages itself`() {
    val patch = emberAt(CENTER)

    world.tick(TICK_INTERVAL)

    assertTrue(world.isAlive(patch))
    assertFalse(world.has(patch, Damage::class))
  }

  private companion object {
    val CENTER = Vec3L(10, 10, 0)
    const val CASTER_ID = 4242L
    const val SKILL_ID = 1000L
    const val DAMAGE_PER_TICK = 7
    const val TICK_INTERVAL = 1.2f
    const val DURATION = 9.6f
  }
}
