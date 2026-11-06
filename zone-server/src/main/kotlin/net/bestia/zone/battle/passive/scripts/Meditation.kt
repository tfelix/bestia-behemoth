package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/**
 * `MEDITATION` (`skills.yml` id 27, `maxLevel: 5`): *"A calm, centered breathing that quickens the
 * natural mending of both Master and Bestia alike. `+6%/lv` HP and Mana regeneration"*.
 *
 * HP and mana only - the authored description does not mention stamina.
 *
 * Note the interaction with integer regeneration: at the level-1 base rate of 2 HP per tick every
 * rank below +50% floors straight back to 2, so this passive only becomes visible on the larger
 * pools of a higher-level character. See `RegenerationCalculator.applyModifier`.
 */
@Component
class Meditation : PassiveSkillScript {

  override val skill = SkillId.M_MEDITATION

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    context.addHpRegen(percent = PERCENT_PER_LEVEL * level)
    context.addManaRegen(percent = PERCENT_PER_LEVEL * level)
  }

  private companion object {
    /** The `+6%/lv` from the skill's own description. */
    const val PERCENT_PER_LEVEL = 6
  }
}
