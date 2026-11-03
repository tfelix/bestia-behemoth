package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * The heal archetype: `(lv + INT) / 8 * (4 + 8 * skillLv) * coef` on the friend it was cast at, the caster itself
 * when none was picked. The AI casts every skill at its enemy, so a heal aimed at a foe lands on the caster.
 */
@Component
class BestiaHealSkill : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.apply(patientOf(ctx), Heal(amountOf(ctx)))

    return null
  }

  companion object {
    private const val MIN_HEAL = 1

    fun amountOf(ctx: SkillContext): Int {
      val caster = ctx.battle.attacker
      val amount = (caster.level + caster.statusValues.intelligence) / 8f *
          (4 + 8 * ctx.battle.usedAttack.level) * ctx.battle.usedAttack.power

      return amount.toInt().coerceAtLeast(MIN_HEAL)
    }

    fun patientOf(ctx: SkillContext): EntityId {
      return ctx.targetEntityId?.takeUnless { ctx.world.isFoe(it) } ?: ctx.casterId
    }
  }
}
