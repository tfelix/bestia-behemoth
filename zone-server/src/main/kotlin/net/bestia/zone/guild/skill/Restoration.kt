package net.bestia.zone.guild.skill

import net.bestia.zone.casting.SkillContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** `skills.yml` id 9019: heals 90% of the HP and mana of the members around the caster. */
@Component
class Restoration(
  tree: GuildSkillTree,
  cooldowns: GuildSkillCooldowns,
) : GuildSkillStrategy(SkillId.RESTORATION, tree, cooldowns) {

  override val cooldownSeconds: Long = 180

  override fun land(ctx: SkillContext, guildId: Long) {
    val centre = ctx.world.positionOf(ctx.casterId) ?: return
    ctx.world.restoreGuildNear(guildId, centre, RADIUS_METRES, HEAL_PERCENT)
  }

  private companion object {
    const val RADIUS_METRES = 30L
    const val HEAL_PERCENT = 90
  }
}
