package net.bestia.zone.battle.ecs.effects

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ForcedMovement
import net.bestia.zone.movement.ecs.Facing
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test

class AreaMechanicsApplierTest {

  private val statusEffects = mockk<StatusEffectService>(relaxed = true)
  private val world = mockk<World>(relaxed = true)
  private val forcedMovement = mockk<ForcedMovement>(relaxed = true)
  private val sut = AreaMechanicsApplier(statusEffects, forcedMovement)

  @Test
  fun `a zone status for everyone lands once on whoever stands inside, foe or friend`() {
    val still = area(AreaMechanics(zoneStatus = StatusEffectId.SHELTERED, zoneStatusForEveryone = true))

    for (id in listOf(FOE, ALLY)) sut.onAnyone(world, id, still)
    sut.onFoe(world, FOE, still, Vec3L(0, 0, 0))
    sut.onAlly(world, ALLY, still)

    verify(exactly = 1) { statusEffects.applyEffect(world, FOE, StatusEffectId.SHELTERED, 1, CASTER) }
    verify(exactly = 1) { statusEffects.applyEffect(world, ALLY, StatusEffectId.SHELTERED, 1, CASTER) }
  }

  @Test
  fun `a wall that counts hits names itself as the source of its allies' status`() {
    val wall = area(AreaMechanics(zoneStatus = StatusEffectId.SAFETY_WALL, forAllies = true, absorbsHits = 6))
    val ward = area(AreaMechanics(zoneStatus = StatusEffectId.SHELTERED, forAllies = true))

    sut.onAlly(world, ALLY, wall, areaId = WALL)
    sut.onAlly(world, ALLY, ward, areaId = WALL)

    verify(exactly = 1) { statusEffects.applyEffect(world, ALLY, StatusEffectId.SAFETY_WALL, 1, WALL) }
    verify(exactly = 1) { statusEffects.applyEffect(world, ALLY, StatusEffectId.SHELTERED, 1, CASTER) }
  }

  @Test
  fun `a wall pushes a foe that walks into it back to the side it came from`() {
    val wall = area(AreaMechanics(knockbackTiles = 2, knockbackAcross = Vec3L(1, 0, 0)))
    standing(FOE, at = Vec3L(5, 5, 0), facing = Facing(dx = 1, dy = 0))

    sut.onFoe(world, FOE, wall, Vec3L(5, 5, 0))

    verify { forcedMovement.awayFrom(world, FOE, Vec3L(6, 5, 0), 2) }
  }

  @Test
  fun `a wall pushes a foe that walks along it, or stands still, away from the caster`() {
    val wall = area(AreaMechanics(knockbackTiles = 1, knockbackAcross = Vec3L(1, 0, 0)))
    standing(FOE, at = Vec3L(5, 5, 0), facing = Facing(dx = 0, dy = 1))
    standing(ALLY, at = Vec3L(5, 6, 0), facing = null)

    sut.onFoe(world, FOE, wall, Vec3L(5, 5, 0))
    sut.onFoe(world, ALLY, wall, Vec3L(5, 6, 0))

    verify { forcedMovement.awayFrom(world, FOE, Vec3L(4, 5, 0), 1) }
    verify { forcedMovement.awayFrom(world, ALLY, Vec3L(4, 6, 0), 1) }
  }

  private fun standing(id: EntityId, at: Vec3L, facing: Facing?) {
    every { world.get(id, Position::class) } returns Position.fromVec3(at)
    every { world.get(id, Facing::class) } returns facing
    // Not immune to knockback.
    every { world.get(id, CombatStats::class) } returns null
  }

  private fun area(mechanics: AreaMechanics) = AreaEffect(
    casterId = CASTER, skillId = 84, skillLevel = 1, radiusTiles = 1,
    damagePerTick = 0, tickIntervalSeconds = 0.5f, remainingTicks = 40, mechanics = mechanics
  )

  private companion object {
    const val CASTER = 1L
    const val FOE = 2L
    const val ALLY = 3L
    const val WALL = 4L
  }
}
