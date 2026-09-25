package net.bestia.zone.account.master.skill

import net.bestia.zone.ecs.battle.skill.KnownSkills
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoviceGateTest {

  private val registry = MasterSkillTreeRegistry().apply {
    load(
      listOf(
        MasterSkillTreeNode(skillId = BASIC_SKILL_ID, maxLevel = 5, tree = "NOVICE"),
        MasterSkillTreeNode(skillId = FIRST_AID_ID, maxLevel = 3, tree = "NOVICE"),
        MasterSkillTreeNode(skillId = CARPENTRY_ID, maxLevel = 5, tree = "CRAFTSMAN"),
        MasterSkillTreeNode(skillId = ORE_REFINEMENT_ID, maxLevel = 5, tree = "CRAFTSMAN", subTree = "BLACKSMITH")
      )
    )
  }

  private val gate = NoviceGate(registry)

  private fun knowing(vararg levels: Pair<Long, Int>): KnownSkills {
    return KnownSkills(levels.toMap().toMutableMap())
  }

  @Test
  fun `a master who has learned nothing is a novice`() {
    assertTrue(gate.isNovice(knowing()))
  }

  /**
   * The whole Novice tree leaves novicehood intact, Basic Skill at its cap included. That is deliberate: Basic
   * Skill 5 is what *opens* the other trees, so ending the gear's life there would take it away at exactly the
   * moment the player has not yet chosen anything.
   */
  @Test
  fun `the Novice tree does not end novicehood, even maxed`() {
    assertTrue(gate.isNovice(knowing(BASIC_SKILL_ID to 5, FIRST_AID_ID to 3)))
  }

  @Test
  fun `one point in another tree ends it`() {
    assertFalse(gate.isNovice(knowing(BASIC_SKILL_ID to 5, CARPENTRY_ID to 1)))
  }

  @Test
  fun `a sub-tree counts as its tree`() {
    assertFalse(gate.isNovice(knowing(ORE_REFINEMENT_ID to 1)))
  }

  /** A catalogued skill nobody has invested in is not an investment - `levelOf` answers 0, not "present". */
  @Test
  fun `a known non-Novice skill at level zero does not end it`() {
    assertTrue(gate.isNovice(knowing(CARPENTRY_ID to 0)))
  }

  /**
   * A player bestia's item-taught skills are catalogued at 1000+ and have no tree node at all, so they cannot
   * end a novicehood the bestia was never really in.
   */
  @Test
  fun `a skill outside the tree entirely is ignored`() {
    assertTrue(gate.isNovice(knowing(BESTIA_SKILL_ID to 1)))
  }

  @Test
  fun `no component at all reads as a novice`() {
    assertTrue(gate.isNovice(null))
  }

  private companion object {
    const val BASIC_SKILL_ID = 1L
    const val FIRST_AID_ID = 8L
    const val CARPENTRY_ID = 20L
    const val ORE_REFINEMENT_ID = 21L
    const val BESTIA_SKILL_ID = 1000L
  }
}
