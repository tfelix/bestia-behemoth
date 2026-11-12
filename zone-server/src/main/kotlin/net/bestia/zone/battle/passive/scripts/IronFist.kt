package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** Iron Fist (`skills.yml` id 143): `+3/lv` ATK with a fist weapon in the right hand, or with nothing in it. */
@Component
class IronFist : PassiveSkillScript {

  override val skill = SkillId.M_IRON_FIST

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    if (!fightsWithFists(context)) return

    context.addAttack(atk = ATK_PER_LEVEL * level)
  }

  private fun fightsWithFists(context: StatusValueRecalcContext): Boolean {
    val isBareHanded = context.heldWeaponType == null && context.heldTool == null

    return context.heldWeaponType == WeaponType.FIST || isBareHanded
  }

  private companion object {
    const val ATK_PER_LEVEL = 3
  }
}
