package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** Owl's Eye (`skills.yml` id 153): `+1/lv` DEX. */
@Component
class OwlsEye : PassiveSkillScript {

  override val skill = SkillId.M_OWLS_EYE

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    context.dexterity += DEX_PER_LEVEL * level
  }

  private companion object {
    const val DEX_PER_LEVEL = 1
  }
}
