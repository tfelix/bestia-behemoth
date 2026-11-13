package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/**
 * Pack Hunt (`skills.yml` id 174): the master's bestias deal `10%/lv` more damage to the prey of the master's Hunter's
 * Mark, see `DamageLanding`. The master itself gains nothing.
 */
@Component
class PackHunt : PassiveSkillScript {

  override val skill = SkillId.M_PACK_HUNT
  override val reachesOwnedBestias = true

  override fun apply(context: StatusValueRecalcContext, level: Int) {
  }

  override fun applyToBestia(context: StatusValueRecalcContext, level: Int) {
    context.markedPreyDamagePercent += PERCENT_PER_LEVEL * level
  }

  private companion object {
    const val PERCENT_PER_LEVEL = 10
  }
}
