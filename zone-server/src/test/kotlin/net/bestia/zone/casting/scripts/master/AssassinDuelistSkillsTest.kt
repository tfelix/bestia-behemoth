package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AssassinDuelistSkillsTest {

  private val world = RecordingSkillWorld()
  private val caster = BattleContextFixture.ATTACKER_ID
  private val target = BattleContextFixture.DEFENDER_ID

  @Test
  fun `Sonic Blow strikes eight times, sharing 340 percent ATK and 40 more each level`() {
    SonicBlow(LineOfSightService()).execute(ctx(level = 1))

    assertEquals(List(8) { target }, world.strikes.map { it.first })
    assertEquals(340, world.strikes.sumOf { it.second })
    assertEquals(listOf(340, 380, 420, 460, 500), (1..5).map { SonicBlow.hitPercents(it).sum() })
    assertEquals(emptyList<Int>(), world.offHandStrikes, "without a second weapon all blows come from the main hand")
  }

  @Test
  fun `a dual wielder's Sonic Blow strikes every second blow with the left hand`() {
    world.dualWielders += caster

    SonicBlow(LineOfSightService()).execute(ctx(level = 5))

    assertEquals(listOf(1, 3, 5, 7), world.offHandStrikes)
    assertEquals(500, world.strikes.sumOf { it.second })
  }

  private fun ctx(level: Int) = SkillContextFixture.skillCtx(world = world, skillLevel = level)
}
