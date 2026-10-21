package net.bestia.zone.skill

import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.bestia.zone.skill.tree.MasterSkillTreeNode
import net.bestia.zone.skill.tree.MasterSkillTreeRegistry

class NoviceGateTest {

  private val registry = MasterSkillTreeRegistry().apply {
    load(
      listOf(
        MasterSkillTreeNode(skillId = BASIC_SKILL_ID, identifier = "BASIC_SKILL", maxLevel = 5, tree = "NOVICE"),
        MasterSkillTreeNode(skillId = FIRST_AID_ID, identifier = "FIRST_AID", maxLevel = 3, tree = "NOVICE"),
        MasterSkillTreeNode(skillId = CARPENTRY_ID, identifier = "CARPENTRY", maxLevel = 5, tree = "CRAFTSMAN"),
        MasterSkillTreeNode(skillId = ORE_REFINEMENT_ID, identifier = "ORE_REFINEMENT", maxLevel = 5, tree = "CRAFTSMAN", subTree = "BLACKSMITH")
      )
    )
  }

  private val gate = NoviceGate(registry)

  private val world = testWorld()

  private fun masterKnowing(vararg levels: Pair<Long, Int>): EntityId {
    return world.createEntity { id ->
      add(id, Master(MASTER_ID))
      add(id, KnownSkills(levels.toMap().toMutableMap()))
    }
  }

  private fun isNovice(entityId: EntityId): Boolean {
    return world.read { gate.isNovice(this, entityId) }
  }

  @Test
  fun `a master who has learned nothing is a novice`() {
    assertTrue(isNovice(masterKnowing()))
  }

  /**
   * The whole Novice tree leaves novicehood intact, Basic Skill at its cap included. That is deliberate: Basic
   * Skill 5 is what *opens* the other trees, so ending the gear's life there would take it away at exactly the
   * moment the player has not yet chosen anything.
   */
  @Test
  fun `the Novice tree does not end novicehood, even maxed`() {
    assertTrue(isNovice(masterKnowing(BASIC_SKILL_ID to 5, FIRST_AID_ID to 3)))
  }

  @Test
  fun `one point in another tree ends it`() {
    assertFalse(isNovice(masterKnowing(BASIC_SKILL_ID to 5, CARPENTRY_ID to 1)))
  }

  @Test
  fun `a sub-tree counts as its tree`() {
    assertFalse(isNovice(masterKnowing(ORE_REFINEMENT_ID to 1)))
  }

  /** A catalogued skill nobody has invested in is not an investment - `levelOf` answers 0, not "present". */
  @Test
  fun `a known non-Novice skill at level zero does not end it`() {
    assertTrue(isNovice(masterKnowing(CARPENTRY_ID to 0)))
  }

  /** Item-taught skills are catalogued at 1000+ and have no tree node, so they say nothing about a tree. */
  @Test
  fun `a skill outside the tree entirely is ignored`() {
    assertTrue(isNovice(masterKnowing(BESTIA_SKILL_ID to 1)))
  }

  @Test
  fun `a master without known skills is a novice`() {
    assertTrue(isNovice(world.createEntity { id -> add(id, Master(MASTER_ID)) }))
  }

  /** Novicehood is the master's training; a bestia must not share the master's starter kit. */
  @Test
  fun `a player bestia is never a novice`() {
    val bestia = world.createEntity { id -> add(id, KnownSkills(mutableMapOf())) }

    assertFalse(isNovice(bestia))
  }

  private companion object {
    const val MASTER_ID = 7L
    const val BASIC_SKILL_ID = 1L
    const val FIRST_AID_ID = 8L
    const val CARPENTRY_ID = 20L
    const val ORE_REFINEMENT_ID = 21L
    const val BESTIA_SKILL_ID = 1000L
  }
}
