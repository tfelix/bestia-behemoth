package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/** Renewal: heals like [BestiaHealSkill] and also washes out poison, wounds, fire, sickness and the like. */
@Component
class BestiaCleansingHeal : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    val patient = BestiaHealSkill.patientOf(ctx)

    CLEANSED.forEach { effect -> if (ctx.world.hasEffect(patient, effect.id)) ctx.world.removeEffect(patient, effect.id) }
    ctx.world.apply(patient, Heal(BestiaHealSkill.amountOf(ctx)))

    return null
  }

  private companion object {
    val CLEANSED = listOf(
      StatusEffectId.POISON, StatusEffectId.BLEEDING, StatusEffectId.BURNING, StatusEffectId.SICK,
      StatusEffectId.BLIND, StatusEffectId.CONFUSED, StatusEffectId.STONING
    )
  }
}
