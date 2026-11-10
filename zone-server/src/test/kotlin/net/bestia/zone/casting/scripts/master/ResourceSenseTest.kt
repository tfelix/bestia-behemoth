package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResourceSenseTest {

  @Test
  fun `each level reaches a kilometre further and senses more exactly`() {
    val senses = (1..5).map { level ->
      val world = RecordingSkillWorld()
      ResourceSense().execute(SkillContextFixture.skillCtx(BattleContextFixture.entityCtx(), world, skillLevel = level))
      world.resourceSenses.single()
    }

    assertEquals(listOf(1000.0, 2000.0, 3000.0, 4000.0, 5000.0), senses.map { it.first })
    assertEquals(listOf(0.1, 0.2, 0.3, 0.4, 0.5), senses.map { it.second }.map { Math.round(it * 10) / 10.0 })
  }
}
