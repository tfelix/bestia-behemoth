package net.bestia.zone.guild.skill

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.skill.SkillId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `skills.yml` id 9021: calls every member of the guild to its War Banner, the caster too - wherever the banner
 * stands, an enemy Stronghold included. Its cooldown is its own, apart from Emergency Recall's.
 */
@Component
class RallyToTheBanner(
  tree: GuildSkillTree,
  cooldowns: GuildSkillCooldowns,
  private val banners: WarBanners,
) : GuildSkillStrategy(SkillId.RALLY_TO_THE_BANNER, tree, cooldowns) {

  override val cooldownSeconds: Long = 900

  override fun refusal(world: World, casterId: EntityId, membership: GuildMembership): OpError? {
    if (banners.of(membership.guildId) == null) {
      return OpError.GUILD_NO_BANNER
    }

    return null
  }

  override fun land(ctx: SkillContext, guildId: Long) {
    val banner = banners.of(guildId) ?: return
    ctx.world.summonGuild(guildId, banner.position, includeCaster = true)
  }
}
