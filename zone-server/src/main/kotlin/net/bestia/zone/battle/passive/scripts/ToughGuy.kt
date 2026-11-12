package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/**
 * Tough Guy (`skills.yml` id 140): `-10%/lv` stamina spent, on skills like Sprint and to the weather alike, and
 * `+10%/lv` stamina regeneration.
 */
@Component
class ToughGuy : PassiveSkillScript {

  override val skill = SkillId.M_TOUGH_GUY

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    context.staminaCostReduction += PERCENT_PER_LEVEL * level
    context.staminaDrainReduction += PERCENT_PER_LEVEL * level
    context.addStaminaRegen(percent = PERCENT_PER_LEVEL * level)
  }

  private companion object {
    const val PERCENT_PER_LEVEL = 10
  }
}
