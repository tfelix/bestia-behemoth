package net.bestia.zone.guild.skill

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.ecs.core.World
import net.bestia.zone.guild.GuildSkillCooldownSMSG
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.skill.SkillId
import net.bestia.zone.util.EntityId

/**
 * A guild skill a member casts for the whole guild. [GuildSkillGrant] decides who may cast it; this adds the
 * cooldown the whole guild shares, and tells every member when it starts.
 */
abstract class GuildSkillStrategy(
  private val skill: SkillId,
  private val tree: GuildSkillTree,
  private val cooldowns: GuildSkillCooldowns,
) : SkillStrategy {

  abstract val cooldownSeconds: Long

  /** What the cast does once the cooldown is claimed. */
  protected abstract fun land(ctx: SkillContext, guildId: Long)

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int): OpError? {
    val membership = world.get(casterId, GuildMembership::class) ?: return OpError.GUILD_NOT_A_MEMBER
    val skillId = tree.find(skill)?.skillId ?: return OpError.REQUEST_REFUSED

    if (cooldowns.remainingMillis(membership.guildId, skillId) > 0) {
      return OpError.GUILD_SKILL_COOLDOWN
    }

    return null
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    val guildId = ctx.world.guildMembershipOf(ctx.casterId)?.guildId ?: return null
    val cooldownMillis = cooldownSeconds * 1_000

    // Claimed here as well as checked at the start: two members may press it in the same tick.
    if (!cooldowns.tryStart(guildId, ctx.skillId, cooldownMillis)) {
      return null
    }

    land(ctx, guildId)
    ctx.world.tellGuild(guildId, GuildSkillCooldownSMSG(ctx.skillId, cooldownMillis))

    return null
  }
}
