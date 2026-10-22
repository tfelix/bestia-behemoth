package net.bestia.zone.guild.skill

import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.GuildMembership
import kotlin.test.Test
import kotlin.test.assertEquals

class GuildSkillGrantTest {

  private val tree = GuildSkillTree().apply {
    load(listOf(GuildSkillNode(skillId = 9017, identifier = "BATTLE_ORDERS", maxLevel = 1)))
  }
  private val sut = GuildSkillGrant(tree)
  private val world = testWorld()

  @Test
  fun `a rank allowed to cast gets the guild's level`() {
    val caster = member(mayUseGuildSkills = true)

    assertEquals(1, sut.grantedLevel(world, caster, 9017))
    assertEquals(0, sut.grantedLevel(world, caster, 5), "not a guild skill")
  }

  @Test
  fun `a rank not allowed to cast gets nothing`() {
    assertEquals(0, sut.grantedLevel(world, member(mayUseGuildSkills = false), 9017))
  }

  private fun member(mayUseGuildSkills: Boolean): Long {
    return world.createEntity { id ->
      add(id, GuildMembership(1, 5, mayUseGuildSkills = mayUseGuildSkills, skillLevels = mapOf("BATTLE_ORDERS" to 1)))
    }
  }
}
