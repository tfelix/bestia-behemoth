package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** Demon Bane (`skills.yml` id 87): `+5%/lv` damage dealt to, and `-6%/lv` taken from, Demons and the Undead. */
@Component
class DemonBane : PassiveSkillScript {

  override val skill = SkillId.M_DEMON_BANE

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    context.addDamageDealt(DEALT_PERCENT_PER_LEVEL * level, unholy = true)
    context.addDamageTaken(TAKEN_PERCENT_PER_LEVEL * level, unholy = true)
  }

  private companion object {
    const val DEALT_PERCENT_PER_LEVEL = 5
    const val TAKEN_PERCENT_PER_LEVEL = 6
  }
}
