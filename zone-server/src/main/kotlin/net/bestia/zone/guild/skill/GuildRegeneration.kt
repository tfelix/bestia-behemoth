package net.bestia.zone.guild.skill

import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** `skills.yml` id 9018: faster HP, mana and stamina regeneration for the members around the caster. */
@Component
class GuildRegeneration(
  tree: GuildSkillTree,
  cooldowns: GuildSkillCooldowns,
) : GuildSkillStrategy(SkillId.GUILD_REGENERATION, tree, cooldowns) {

  override val cooldownSeconds: Long = 300

  override fun land(ctx: SkillContext, guildId: Long) {
    val centre = ctx.world.positionOf(ctx.casterId) ?: return
    ctx.world.applyStatusEffectToGuildNear(
      guildId, centre, RADIUS_METRES, StatusEffectId.GUILD_REGENERATION.id, ctx.skillLevel
    )
  }

  private companion object {
    const val RADIUS_METRES = 30L
  }
}
