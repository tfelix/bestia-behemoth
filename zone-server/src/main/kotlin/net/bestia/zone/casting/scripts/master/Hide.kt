package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/** Hide (`skills.yml` id 122): the caster goes [StatusEffectId.HIDING] until a recast, a step or an attack. */
@Component
class Hide : SkillStrategy {

  override val toggledStatus: StatusEffectId = StatusEffectId.HIDING

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.applyStatusEffect(ctx.casterId, StatusEffectId.HIDING.id, level = ctx.skillLevel.coerceIn(1, MAX_LEVEL))

    return null
  }

  private companion object {
    const val MAX_LEVEL = 5
  }
}
