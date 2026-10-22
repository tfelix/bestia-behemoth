package net.bestia.zone.guild.skill

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.skill.SkillId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** `skills.yml` id 9020: calls every member of the guild to the caster, inside the guild's own Stronghold. */
@Component
class EmergencyRecall(
  tree: GuildSkillTree,
  cooldowns: GuildSkillCooldowns,
  private val strongholds: GuildStrongholds,
) : GuildSkillStrategy(SkillId.EMERGENCY_RECALL, tree, cooldowns) {

  override val cooldownSeconds: Long = 300

  override fun refusal(world: World, casterId: EntityId, membership: GuildMembership): OpError? {
    if (!strongholds.isInOwnStronghold(world, casterId, membership.guildId)) {
      return OpError.GUILD_NOT_IN_STRONGHOLD
    }

    return null
  }

  override fun land(ctx: SkillContext, guildId: Long) {
    val centre = ctx.world.positionOf(ctx.casterId) ?: return
    ctx.world.summonGuild(guildId, centre, includeCaster = false)
  }
}
