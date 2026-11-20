package net.bestia.zone.guild.skill

import net.bestia.zone.casting.SkillContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** `skills.yml` id 1001: senses the awake Empyrite Seeds around the caster, where a Stronghold may be founded. */
@Component
class OfficialGuildApproval(
  tree: GuildSkillTree,
  cooldowns: GuildSkillCooldowns,
) : GuildSkillStrategy(SkillId.OFFICIAL_GUILD_APPROVAL, tree, cooldowns) {

  override val cooldownSeconds: Long = 60

  override fun land(ctx: SkillContext, guildId: Long) {
    ctx.world.senseEmpyriteSeeds()
  }
}
