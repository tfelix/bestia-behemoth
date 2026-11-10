package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** Maximize Carry Capacity (`skills.yml` id 35): `+15%/lv` weight limit for the master and every bestia. */
@Component
class MaximizeCarryCapacity : PassiveSkillScript {

  override val skill = SkillId.M_MAXIMIZE_CARRY_CAPACITY
  override val reachesOwnedBestias = true

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    context.carryCapacityPercent += PERCENT_PER_LEVEL * level
  }

  private companion object {
    const val PERCENT_PER_LEVEL = 15
  }
}
