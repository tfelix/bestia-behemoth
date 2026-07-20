package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.HitDamage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FireboltTest {

  private val sut = Firebolt(LineOfSightService())

  @Test
  fun `damage scales with skill level`() {
    val lowLevelCast = sut.doAttack(ctxAt(skillLevel = 1))
    val highLevelCast = sut.doAttack(ctxAt(skillLevel = 10))

    assertTrue(
      highLevelCast.amount > lowLevelCast.amount,
      "Firebolt at Lv.10 (${highLevelCast.amount}) should out-damage Lv.1 (${lowLevelCast.amount})"
    )
  }

  @Test
  fun `damage scales with intelligence`() {
    val dullCast = sut.doAttack(ctxAt(skillLevel = 5, intelligence = 10))
    val smartCast = sut.doAttack(ctxAt(skillLevel = 5, intelligence = 100))

    assertTrue(
      smartCast.amount > dullCast.amount,
      "Firebolt should scale with INT (${dullCast.amount} -> ${smartCast.amount})"
    )
  }

  @Test
  fun `a landed bolt always deals at least one damage`() {
    // The fixture's defender out-defends a Lv.1 caster with minimal stats, which would otherwise
    // produce zero or negative damage - HitDamage would then reject the negative amount outright.
    val damage = sut.doAttack(ctxAt(skillLevel = 1, level = 1, intelligence = 1))

    assertTrue(damage is HitDamage)
    assertTrue(damage.amount >= 1, "Expected at least 1 damage but was ${damage.amount}")
  }

  @Test
  fun `is a hit rather than a miss against an entity`() {
    val damage = sut.doAttack(ctxAt(skillLevel = 3))

    assertEquals(HitDamage::class, damage::class)
  }

  private fun ctxAt(skillLevel: Int, level: Int = 10, intelligence: Int = 10) =
    BattleContextFixture.entityCtx(
      attack = BattleContextFixture.attack(skillLevel),
      attackerEntity = BattleContextFixture.battleEntity(level = level, intelligence = intelligence)
    )
}
