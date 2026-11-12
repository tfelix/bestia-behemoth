package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** Auto Guard (`skills.yml` id 137): with a shield, a `4%/lv` chance to block a weapon blow whole. */
@Component
class AutoGuard : PassiveSkillScript {

  override val skill = SkillId.M_AUTO_GUARD

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    if (context.shieldArmorType == null) return

    context.blockChancePercent += BLOCK_PERCENT_PER_LEVEL * level
  }

  private companion object {
    const val BLOCK_PERCENT_PER_LEVEL = 4
  }
}
