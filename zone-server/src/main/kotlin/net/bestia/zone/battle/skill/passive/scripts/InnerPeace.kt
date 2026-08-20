package net.bestia.zone.battle.skill.passive.scripts

import net.bestia.zone.battle.skill.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/**
 * `INNER_PEACE` (`skills.yml` id 27, `maxLevel: 10`): *"Your Bestia and Master gain an increased HP
 * and Mana regeneration rate. `+3% effect/lv`"*.
 *
 * HP and mana only - the authored description does not mention stamina, and
 * `WILDERNESS_SURVIVAL` is the stamina-facing passive (phrased as a drain reduction, which belongs
 * to `EnvironmentalExposureSystem` rather than here).
 *
 * Note the interaction with integer regeneration: at the level-1 base rate of 2 HP per tick every
 * rank below +50% floors straight back to 2, so this passive only becomes visible on the larger
 * pools of a higher-level character. See `RegenerationCalculator.applyModifier`.
 */
@Component
class InnerPeace : PassiveSkillScript {

  override val skill = SkillId.INNER_PEACE

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    context.addHpRegen(percent = PERCENT_PER_LEVEL * level)
    context.addManaRegen(percent = PERCENT_PER_LEVEL * level)
  }

  private companion object {
    /** The `+3% effect/lv` from the skill's own description. */
    const val PERCENT_PER_LEVEL = 3
  }
}
