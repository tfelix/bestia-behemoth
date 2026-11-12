package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/**
 * Poison Research (`skills.yml` id 124): `+10%/lv` resistance to poison. Its `+20%/lv` drain is for the poison a
 * researcher's own blade leaves, see `PoisonedBlade`.
 */
@Component
class PoisonResearch : PassiveSkillScript {

  override val skill = SkillId.M_POISON_RESEARCH

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    context.addStatusResistance(StatusEffectId.POISON, RESISTANCE_PERCENT_PER_LEVEL * level.coerceIn(0, MAX_LEVEL))
  }

  companion object {
    private const val MAX_LEVEL = 5
    private const val RESISTANCE_PERCENT_PER_LEVEL = 10
    private const val DRAIN_PERCENT_PER_LEVEL = 20

    /** A poison's drain, in per mille of the maximum HP, after its brewer's Poison Research at [level]. */
    fun researchedDrain(drainPerMille: Int, level: Int): Int {
      return drainPerMille * (100 + DRAIN_PERCENT_PER_LEVEL * level.coerceIn(0, MAX_LEVEL)) / 100
    }
  }
}
