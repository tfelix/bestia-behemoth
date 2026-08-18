package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.AreaEffectResult
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.roundToInt

class EmberTest {

  private val sut = Ember(LineOfSightService())

  @Test
  fun `a ground cast leaves a patch that ticks eight times`() {
    val result = sut.execute(groundCtx())

    assertTrue(result is AreaEffectResult)
    result as AreaEffectResult
    assertEquals(1.2f, result.tickIntervalSeconds)
    assertEquals(9.6f, result.durationSeconds)
    assertEquals(8, (result.durationSeconds / result.tickIntervalSeconds).roundToInt())
  }

  @Test
  fun `the burnt area is the one the catalogue declares rather than one the script picked`() {
    val narrow = sut.execute(groundCtx(aoeRadius = 1.0)) as AreaEffectResult
    val wide = sut.execute(groundCtx(aoeRadius = 3.0)) as AreaEffectResult

    assertEquals(1L, narrow.radiusTiles)
    assertEquals(3L, wide.radiusTiles)
  }

  @Test
  fun `the flames do not care whose side anyone is on`() {
    val result = sut.execute(groundCtx()) as AreaEffectResult

    assertTrue(result.hitsCaster, "Ember burns its own caster if they stand in it")
  }

  @Test
  fun `damage per tick scales with intelligence`() {
    val dull = sut.execute(groundCtx(intelligence = 10)) as AreaEffectResult
    val smart = sut.execute(groundCtx(intelligence = 100)) as AreaEffectResult

    assertTrue(
      smart.damagePerTick > dull.damagePerTick,
      "Ember should scale with INT (${dull.damagePerTick} -> ${smart.damagePerTick})"
    )
  }

  @Test
  fun `every tick burns for at least one`() {
    val result = sut.execute(groundCtx(level = 1, intelligence = 1)) as AreaEffectResult

    assertTrue(result.damagePerTick >= 1, "expected at least 1 per tick but was ${result.damagePerTick}")
  }

  @Test
  fun `aimed at an entity it does nothing at all`() {
    val ctx = BattleContextFixture.entityCtx(attack = BattleContextFixture.attack(aoeRadius = 1.0))

    assertEquals(Miss, sut.execute(ctx))
    assertFalse(sut.isAttackPossible(ctx), "Ember is a ground skill and must refuse an entity target")
  }

  @Test
  fun `a point beyond the skill range is refused`() {
    assertTrue(sut.isAttackPossible(groundCtx(targetPosition = Vec3L(3, 0, 0))))
    assertFalse(sut.isAttackPossible(groundCtx(targetPosition = Vec3L(500, 0, 0))))
  }

  private fun groundCtx(
    aoeRadius: Double = 1.0,
    level: Int = 10,
    intelligence: Int = 10,
    targetPosition: Vec3L = Vec3L(3, 0, 0)
  ) = BattleContextFixture.groundCtx(
    attack = BattleContextFixture.attack(aoeRadius = aoeRadius),
    attackerEntity = BattleContextFixture.battleEntity(level = level, intelligence = intelligence),
    targetPosition = targetPosition
  )
}
