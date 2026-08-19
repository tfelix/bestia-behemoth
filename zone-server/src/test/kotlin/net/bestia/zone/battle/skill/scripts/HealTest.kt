package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.skill.SkillContextFixture
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HealTest {

  private val sut = Heal(LineOfSightService())

  @Test
  fun `heal scales with skill level, character level and intelligence`() {
    val amounts = mutableMapOf<Triple<Int, Int, Int>, Int>()

    for (skillLv in listOf(1, 5, 10)) {
      for (charLv in listOf(1, 10, 50, 100)) {
        for (int in listOf(1, 10, 50, 100, 140)) {
          amounts[Triple(skillLv, charLv, int)] = healed(skillLv, charLv, int)
        }
      }
    }

    // Monotonic in each axis independently: a rank, a level or a point of INT must never make a heal worse.
    for ((key, amount) in amounts) {
      val (skillLv, charLv, int) = key
      amounts[Triple(skillLv + 5, charLv, int)]?.let {
        assertTrue(it >= amount, "rank $skillLv->${skillLv + 5} at Lv$charLv/INT$int went $amount -> $it")
      }
      amounts[Triple(skillLv, charLv * 10, int)]?.let {
        assertTrue(it >= amount, "level $charLv->${charLv * 10} at rank $skillLv/INT$int went $amount -> $it")
      }
      amounts[Triple(skillLv, charLv, int * 10)]?.let {
        assertTrue(it >= amount, "INT $int->${int * 10} at rank $skillLv/Lv$charLv went $amount -> $it")
      }
    }
  }

  private fun healed(skillLevel: Int, level: Int, intelligence: Int): Int {
    val ctx = SkillContextFixture.skillCtx(
      BattleContextFixture.entityCtx(
        attack = BattleContextFixture.attack(skillLevel),
        attackerEntity = BattleContextFixture.battleEntity(level = level, intelligence = intelligence)
      )
    )

    return sut.execute(ctx)?.amount ?: error("Heal must always produce a number against an entity")
  }
}
