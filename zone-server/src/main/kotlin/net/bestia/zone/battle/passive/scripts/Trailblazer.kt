package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** Trailblazer (`skills.yml` id 151): the master walks `3%/lv` faster on ground nobody made, see `OffRoadSystem`. */
@Component
class Trailblazer : PassiveSkillScript {

  override val skill = SkillId.M_TRAILBLAZER

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    val percent = PERCENT_PER_LEVEL * level
    context.offRoadSpeedPercent += percent

    if (context.walksOffRoad) {
      context.speed *= 1f + percent / 100f
    }
  }

  private companion object {
    const val PERCENT_PER_LEVEL = 3
  }
}
