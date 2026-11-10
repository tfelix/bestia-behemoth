package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** Quick Travel (`skills.yml` id 29): `+3%/lv` movement speed for the master and the master's bestias. */
@Component
class QuickTravel : PassiveSkillScript {

  override val skill = SkillId.M_QUICK_TRAVEL
  override val reachesOwnedBestias = true

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    context.speed *= 1f + PERCENT_PER_LEVEL * level / 100f
  }

  private companion object {
    const val PERCENT_PER_LEVEL = 3
  }
}
