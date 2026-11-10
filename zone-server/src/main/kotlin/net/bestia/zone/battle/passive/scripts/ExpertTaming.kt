package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/**
 * Expert Taming (`skills.yml` id 33), for the master's bestias alone: `-2%/lv` stamina loss, to the weather and to
 * their own skills. The `+5%/lv` EXP is granted where experience is, in `GainExpSystem`.
 */
@Component
class ExpertTaming : PassiveSkillScript {

  override val skill = SkillId.M_EXPERT_TAMING
  override val reachesOwnedBestias = true

  /** Nothing for the master: the patience is spent on the bestias. */
  override fun apply(context: StatusValueRecalcContext, level: Int) {
  }

  override fun applyToBestia(context: StatusValueRecalcContext, level: Int) {
    context.staminaCostReduction += STAMINA_PERCENT_PER_LEVEL * level
    context.staminaDrainReduction += STAMINA_PERCENT_PER_LEVEL * level
  }

  companion object {
    const val STAMINA_PERCENT_PER_LEVEL = 2
    const val EXP_PERCENT_PER_LEVEL = 5
  }
}
