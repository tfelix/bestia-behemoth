package net.bestia.zone.guild.skill

import net.bestia.zone.guild.skill.GuildSkillNode.Prerequisite
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuildSkillTreeTest {

  private val emblem = GuildSkillNode(skillId = 1, identifier = "EMBLEM", maxLevel = 1)
  private val approval = GuildSkillNode(
    skillId = 2,
    identifier = "APPROVAL",
    maxLevel = 1,
    prerequisites = listOf(Prerequisite(skillId = 1, level = 1)),
  )

  @Test
  fun `a skill can be raised once its prerequisites are met, up to its maximum`() {
    val sut = GuildSkillTree().apply { load(listOf(emblem, approval)) }

    assertFalse(sut.mayRaise(approval, levels()), "needs the emblem")
    assertTrue(sut.mayRaise(approval, levels("EMBLEM" to 1)))
    assertFalse(sut.mayRaise(approval, levels("EMBLEM" to 1, "APPROVAL" to 1)), "already at its maximum")
  }

  @Test
  fun `a prerequisite outside the tree fails the load`() {
    assertThrows<IllegalStateException> { GuildSkillTree().load(listOf(approval)) }
  }

  @Test
  fun `a prerequisite above its skill's maximum fails the load`() {
    val tooHigh = approval.copy(prerequisites = listOf(Prerequisite(skillId = 1, level = 2)))

    assertThrows<IllegalArgumentException> { GuildSkillTree().load(listOf(emblem, tooHigh)) }
  }

  @Test
  fun `a cycle fails the load`() {
    val circularEmblem = emblem.copy(prerequisites = listOf(Prerequisite(skillId = 2, level = 1)))

    assertThrows<IllegalArgumentException> { GuildSkillTree().load(listOf(circularEmblem, approval)) }
  }

  private fun levels(vararg levels: Pair<String, Int>): (String) -> Int {
    val byIdentifier = levels.toMap()

    return { identifier -> byIdentifier[identifier] ?: 0 }
  }
}
