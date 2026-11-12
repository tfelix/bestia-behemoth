package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/**
 * Camouflage (`skills.yml` id 150): wild creatures notice the master `10%/lv` closer than they would. It hides
 * nothing, see `NoticeRange`.
 */
@Component
class Camouflage : PassiveSkillScript {

  override val skill = SkillId.M_CAMOUFLAGE

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    context.noticeRangeCutPercent += PERCENT_PER_LEVEL * level
  }

  private companion object {
    const val PERCENT_PER_LEVEL = 10
  }
}
