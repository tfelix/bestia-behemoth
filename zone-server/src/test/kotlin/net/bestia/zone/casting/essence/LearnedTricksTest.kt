package net.bestia.zone.casting.essence

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.EssenceSkill
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.skill.SkillIds
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LearnedTricksTest {

  private val world = testWorld()
  private val skillIds = SkillIds().apply { load(mapOf("shadow_cloak" to SHADOW_CLOAK)) }
  private val bestias = mockk<BestiaCatalogue> { every { attackLevelOf(SHADOW_CLOAK) } returns 26 }
  private val tricks = LearnedTricks(skillIds, bestias)

  @Test
  fun `the wearer may cast the trick at level one`() {
    val wearer = wearing(EssenceSkill("shadow_cloak", attackLevelCap = 40))

    assertEquals(1, tricks.grantedLevel(world, wearer, SHADOW_CLOAK))
    assertEquals(listOf(SHADOW_CLOAK), tricks.of(world, wearer))
  }

  /** An Amethyst caps at attack level 20, and Shadow Cloak is learned at 26. */
  @Test
  fun `a gem too weak for the attack grants nothing`() {
    val wearer = wearing(EssenceSkill("shadow_cloak", attackLevelCap = 20))

    assertEquals(0, tricks.grantedLevel(world, wearer, SHADOW_CLOAK))
  }

  private fun wearing(trick: EssenceSkill): EntityId = world.createEntity { id ->
    add(id, CombatStats(CombatValues.OBJECT_BASELINE.copy(learnedTricks = listOf(trick))))
  }

  private companion object {
    const val SHADOW_CLOAK = 2161L
  }
}
