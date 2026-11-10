package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EchoSoundingTest {

  @Test
  fun `each level listens 5 m further, and from level 3 tells depth and size`() {
    val soundings = (1..5).map { level ->
      val world = RecordingSkillWorld()
      EchoSounding().execute(SkillContextFixture.skillCtx(BattleContextFixture.entityCtx(), world, skillLevel = level))
      world.soundings.single()
    }

    assertEquals(listOf(10 to false, 15 to false, 20 to true, 25 to true, 30 to true), soundings)
  }
}
