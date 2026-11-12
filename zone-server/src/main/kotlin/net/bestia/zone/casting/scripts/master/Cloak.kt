package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/** Cloak (`skills.yml` id 123): the caster goes [StatusEffectId.CLOAKING] until a recast or an attack. */
@Component
class Cloak : SkillStrategy {

  override val toggledStatus: StatusEffectId = StatusEffectId.CLOAKING

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.applyStatusEffect(ctx.casterId, StatusEffectId.CLOAKING.id, level = ctx.skillLevel.coerceIn(1, MAX_LEVEL))

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
  }
}
