package net.bestia.zone.guild.skill

import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** `skills.yml` id 9016: the members around the caster move 25% faster for a short time. */
@Component
class EmergencyMove(
  tree: GuildSkillTree,
  cooldowns: GuildSkillCooldowns,
) : GuildSkillStrategy(SkillId.EMERGENCY_MOVE, tree, cooldowns) {

  override val cooldownSeconds: Long = 60

  override fun land(ctx: SkillContext, guildId: Long) {
    val centre = ctx.world.positionOf(ctx.casterId) ?: return
    ctx.world.applyStatusEffectToGuildNear(guildId, centre, RADIUS_METRES, StatusEffectId.EMERGENCY_MOVE.id, 1)
  }

  private companion object {
    const val RADIUS_METRES = 15L
  }
}
