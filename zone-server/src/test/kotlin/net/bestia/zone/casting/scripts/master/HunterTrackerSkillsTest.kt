package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillContextFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HunterTrackerSkillsTest {

  private val world = RecordingSkillWorld()
  private val target = BattleContextFixture.DEFENDER_ID

  @Test
  fun `Hunter's Mark marks a foe as prey at the cast's level`() {
    world.foes += target
    val mark = HuntersMark(LineOfSightService())

    assertTrue(mark.isCastPossible(ctx(level = 4)))
    mark.execute(ctx(level = 4))

    assertEquals(listOf(target to 4), world.markedPrey)
  }

  @Test
  fun `Hunter's Mark is not cast on a friend`() {
    assertFalse(HuntersMark(LineOfSightService()).isCastPossible(ctx(level = 1)))
  }

  private fun ctx(level: Int): SkillContext {
    return SkillContextFixture.skillCtx(world = world, skillLevel = level)
  }
}
