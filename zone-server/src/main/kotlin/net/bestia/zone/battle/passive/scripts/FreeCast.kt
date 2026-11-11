package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** Free Cast (`skills.yml` id 62): `+20%/lv` of the walking speed kept while casting, `+10%/lv` interrupt resistance. */
@Component
class FreeCast : PassiveSkillScript {

  override val skill = SkillId.M_FREE_CAST

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    context.castMovePercent += MOVE_PERCENT_PER_LEVEL * level
    context.castInterruptResistance += RESISTANCE_PER_LEVEL * level
  }

  private companion object {
    const val MOVE_PERCENT_PER_LEVEL = 20
    const val RESISTANCE_PER_LEVEL = 10
  }
}
