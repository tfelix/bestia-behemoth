package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * Suppress Magic (`skills.yml` id 105): one `20%/lv` roll. A win breaks the target's running cast and leaves it
 * [StatusEffectId.SILENCED]; a loss does nothing.
 */
@Component
class SuppressMagic(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)

    val landed = ctx.world.inflict(target, StatusEffectId.SILENCED.name, CHANCE_PERCENT_PER_LEVEL * level, null, level)
    // Silenced breaks the cast as it lands, but a refresh of one already there does not land again.
    if (landed) {
      ctx.world.interrupt(target)
    }

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
    const val CHANCE_PERCENT_PER_LEVEL = 20
  }
}
