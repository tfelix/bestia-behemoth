package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/** The buff archetype: the bestia empowers itself, whatever was targeted - see [BestiaHealSkill]. */
@Component
class BestiaBuffSkill : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    ctx.applyStatusEffect(ctx.casterId, StatusEffectId.EMPOWERED)

    return null
  }
}
