package net.bestia.zone.guild.skill

import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** `skills.yml` id 9017: +5 STR, DEX and INT for the members around the caster. */
@Component
class BattleOrders(
  tree: GuildSkillTree,
  cooldowns: GuildSkillCooldowns,
) : GuildSkillStrategy(SkillId.BATTLE_ORDERS, tree, cooldowns) {

  override val cooldownSeconds: Long = 180

  override fun land(ctx: SkillContext, guildId: Long) {
    val centre = ctx.world.positionOf(ctx.casterId) ?: return
    ctx.world.applyStatusEffectToGuildNear(guildId, centre, RADIUS_METRES, StatusEffectId.BATTLE_ORDERS.id, ctx.skillLevel)
  }

  private companion object {
    const val RADIUS_METRES = 30L
  }
}
