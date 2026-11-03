package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * Bends a wild undead, no boss and not far above the caster, to fight its own kind for a while, see `Hostility`.
 * The target may resist it.
 */
@Component
class ControlUndead(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val battle = ctx.battle as? EntityBattleContext ?: return false
    val nature = ctx.targetEntityId?.let { ctx.world.natureOf(it) } ?: return false

    return Undeath.isUndead(nature) && !nature.boss &&
        battle.defender.level <= battle.attacker.level + MAX_LEVEL_LEAD &&
        super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    val seconds = BASE_SECONDS + SECONDS_PER_LEVEL * ctx.skillLevel

    ctx.world.inflict(target, StatusEffectId.CONTROLLED.name, chancePercent = 100, seconds, ctx.skillLevel)

    return null
  }

  private companion object {
    const val MAX_LEVEL_LEAD = 5
    const val BASE_SECONDS = 20.0
    const val SECONDS_PER_LEVEL = 2.0
  }
}
