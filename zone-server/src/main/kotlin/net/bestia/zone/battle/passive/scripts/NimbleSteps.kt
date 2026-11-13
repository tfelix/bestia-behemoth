package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.gear.ArmorType
import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** Nimble Steps (`skills.yml` id 161): `+2/lv` FLEE while the body wears light armor, cloth or nothing. */
@Component
class NimbleSteps : PassiveSkillScript {

  override val skill = SkillId.M_NIMBLE_STEPS

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    if (!wearsLightOrNothing(context)) return

    context.addAccuracy(flee = FLEE_PER_LEVEL * level)
  }

  private fun wearsLightOrNothing(context: StatusValueRecalcContext): Boolean {
    val body = context.bodyArmorType

    return body == null || body == ArmorType.CLOTH || body == ArmorType.LIGHT
  }

  private companion object {
    const val FLEE_PER_LEVEL = 2
  }
}
