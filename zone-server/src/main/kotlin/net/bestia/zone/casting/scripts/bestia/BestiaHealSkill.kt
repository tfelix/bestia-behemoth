package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/**
 * The heal archetype: the bestia heals itself by `(lv + INT) / 8 * (4 + 8 * skillLv) * coef`. Always the
 * caster, whatever was targeted, because the AI casts every attack skill at its enemy.
 */
@Component
class BestiaHealSkill : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    val caster = ctx.battle.attacker
    val amount = (caster.level + caster.statusValues.intelligence) / 8f *
        (4 + 8 * ctx.battle.usedAttack.level) * ctx.battle.usedAttack.power

    ctx.world.apply(ctx.casterId, Heal(amount.toInt().coerceAtLeast(MIN_HEAL)))

    return null
  }

  private companion object {
    const val MIN_HEAL = 1
  }
}
