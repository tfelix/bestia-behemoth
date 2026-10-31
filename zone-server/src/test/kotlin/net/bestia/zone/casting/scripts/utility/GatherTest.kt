package net.bestia.zone.casting.scripts.utility

import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.skill.persistence.SkillYield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Random

class GatherTest {

  @Test
  fun `a certain yield always drops at the caster's feet and an impossible one never does`() {
    val at = Vec3L(4, 2, 0)
    val world = RecordingSkillWorld().putPosition(1L, at)
    val skill = SkillContextFixture.skill().apply {
      yields = mutableListOf(SkillYield(itemId = WOOL, chance = 10_000), SkillYield(itemId = GEM, chance = 0))
    }

    Gather(Random(7)).execute(SkillContextFixture.skillCtx(world = world, skill = skill))

    assertEquals(listOf(RecordingSkillWorld.DroppedItem(WOOL, 1, at)), world.droppedItems)
  }

  private companion object {
    const val WOOL = 52L
    const val GEM = 63L
  }
}
