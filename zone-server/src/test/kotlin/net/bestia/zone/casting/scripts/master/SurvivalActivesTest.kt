package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SurvivalActivesTest {

  private val world = RecordingSkillWorld()

  private fun groundCast(level: Int): SkillContext = SkillContextFixture.skillCtx(
    BattleContextFixture.groundCtx(attack = BattleContextFixture.attack(level)), world
  )

  @Test
  fun `replanting grows faster each level and gives one more log at three`() {
    val sut = Replanting()
    (1..3).forEach { sut.execute(groundCast(it)) }

    assertEquals(listOf(75 to 0, 50 to 0, 25 to 1), world.replanted.map { it.second to it.third })
  }

  @Test
  fun `a base camp stands thirty minutes per level`() {
    val sut = BaseCamp()
    (1..3).forEach { sut.execute(groundCast(it)) }

    assertEquals(listOf(1800L, 3600L, 5400L), world.camps.map { it.third })
  }
}
