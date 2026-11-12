package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.damage.HitForm
import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** Iron Skin (`skills.yml` id 97): `-3%/lv` physical damage taken. */
@Component
class IronSkin : PassiveSkillScript {

  override val skill = SkillId.M_IRON_SKIN

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    context.addDamageTaken(TAKEN_PERCENT_PER_LEVEL * level, form = HitForm.PHYSICAL)
  }

  private companion object {
    const val TAKEN_PERCENT_PER_LEVEL = 3
  }
}
