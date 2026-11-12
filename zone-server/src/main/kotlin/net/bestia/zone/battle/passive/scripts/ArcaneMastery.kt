package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** Arcane Mastery (`skills.yml` id 102): `+10%/lv` from 5% at Lv.1 on Soul Break's damage and Mindbreak's MATK. */
@Component
class ArcaneMastery : PassiveSkillScript {

  override val skill = SkillId.M_ARCANE_MASTERY

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    context.arcaneDamagePercent += FIRST_LEVEL_PERCENT + PERCENT_PER_LEVEL * (level - 1)
  }

  private companion object {
    const val FIRST_LEVEL_PERCENT = 5
    const val PERCENT_PER_LEVEL = 10
  }
}
