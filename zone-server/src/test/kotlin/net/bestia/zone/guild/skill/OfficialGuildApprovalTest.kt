package net.bestia.zone.guild.skill

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.GuildMembership
import kotlin.test.Test
import kotlin.test.assertEquals

class OfficialGuildApprovalTest {

  private val tree = GuildSkillTree().apply {
    load(listOf(GuildSkillNode(skillId = APPROVAL_ID, identifier = "OFFICIAL_GUILD_APPROVAL", maxLevel = 1)))
  }
  private val cooldowns = GuildSkillCooldowns()
  private val approval = OfficialGuildApproval(tree, cooldowns)
  private val membership = GuildMembership(GUILD, taxPercent = 5, mayUseGuildSkills = true)

  @Test
  fun `a cast senses the seeds, and the whole guild waits a minute for the next`() {
    val world = RecordingSkillWorld().putMembership(BattleContextFixture.ATTACKER_ID, membership)
    val ecs = testWorld()
    val caster = ecs.createEntity { id -> add(id, membership) }

    approval.execute(SkillContextFixture.skillCtx(world = world, skillId = APPROVAL_ID, skillLevel = 1))

    assertEquals(1, world.seedSenses)
    assertEquals(OpError.GUILD_SKILL_COOLDOWN, approval.checkCastStart(ecs, caster, 1))
  }

  private companion object {
    const val GUILD = 1L
    const val APPROVAL_ID = 1001L
  }
}
