package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/** Increase AGI (`skills.yml` id 81): hastens an ally with [StatusEffectId.INCREASE_AGI]. */
@Component
class IncreaseAgi(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean = ctx.battle is EntityBattleContext && super.isCastPossible(ctx)

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    ctx.world.applyStatusEffect(target, StatusEffectId.INCREASE_AGI.id, level = ctx.skillLevel.coerceIn(1, MAX_LEVEL))

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
  }
}
