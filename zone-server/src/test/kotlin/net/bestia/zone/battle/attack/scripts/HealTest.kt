package net.bestia.zone.battle.attack.scripts

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.BattleContextFixture
import org.junit.jupiter.api.Test

class HealTest {

  private val sut = Heal(LineOfSightService())

  @Test
  fun `heal scales with skill level`() {
    val skillLevels = listOf(1, 5, 10)
    val levels = listOf(1, 10, 20, 50, 80, 90, 100, 110)
    val intelligenceValues = listOf(1, 10, 20, 50, 80, 100, 110, 120, 130, 140)

    println("SkillLv | CharLv | Int | Heal")
    println("-----------------------------")

    for (skillLv in skillLevels) {
      for (charLv in levels) {
        for (int in intelligenceValues) {
          val attack = BattleContextFixture.attack(skillLv)
          val ctx = BattleContextFixture.entityCtx(
            attack = attack,
            attackerEntity = BattleContextFixture.battleEntity(
              level = charLv,
              intelligence = int
            ),
          )

          val heal = sut.doAttack(ctx)

          println(String.format("%7d | %6d | %3d | %4d", skillLv, charLv, int, heal.amount))
        }
      }
    }
  }
}