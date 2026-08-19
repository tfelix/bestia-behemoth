package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.skill.SkillContextFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FireboltTest {

  private val sut = Firebolt(LineOfSightService())

  @Test
  fun `damage scales with skill level`() {
    val lowLevelCast = cast(skillLevel = 1)
    val highLevelCast = cast(skillLevel = 10)

    assertTrue(
      highLevelCast > lowLevelCast,
      "Firebolt at Lv.10 ($highLevelCast) should out-damage Lv.1 ($lowLevelCast)"
    )
  }

  @Test
  fun `damage scales with intelligence`() {
    val dullCast = cast(skillLevel = 5, intelligence = 10)
    val smartCast = cast(skillLevel = 5, intelligence = 100)

    assertTrue(smartCast > dullCast, "Firebolt should scale with INT ($dullCast -> $smartCast)")
  }

  @Test
  fun `a landed bolt always deals at least one damage`() {
    // The fixture's defender out-defends a Lv.1 caster with minimal stats, which would otherwise produce
    // zero or negative damage - HitDamage would then reject the negative amount outright.
    val damage = sut.execute(ctxAt(skillLevel = 1, level = 1, intelligence = 1))

    assertTrue(damage is HitDamage)
    assertTrue(damage!!.amount >= 1, "Expected at least 1 damage but was ${damage.amount}")
  }

  @Test
  fun `is a hit rather than a miss against an entity`() {
    val damage = sut.execute(ctxAt(skillLevel = 3))

    assertNotNull(damage)
    assertEquals(HitDamage::class, damage!!::class)
  }

  private fun cast(skillLevel: Int, level: Int = 10, intelligence: Int = 10): Int =
    sut.execute(ctxAt(skillLevel, level, intelligence))?.amount
      ?: error("Firebolt must always produce a number against an entity")

  private fun ctxAt(skillLevel: Int, level: Int = 10, intelligence: Int = 10) =
    SkillContextFixture.skillCtx(
      BattleContextFixture.entityCtx(
        attack = BattleContextFixture.attack(skillLevel),
        attackerEntity = BattleContextFixture.battleEntity(level = level, intelligence = intelligence)
      )
    )
}
