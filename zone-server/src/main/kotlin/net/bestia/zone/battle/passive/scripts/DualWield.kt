package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/**
 * Dual Wield (`skills.yml` id 120): a second weapon in the left hand strikes after every swing at `30% + 10%/lv` of
 * a blow, and swings come `25% - 5%/lv` slower. Holding the weapon at all is the equip rules', see `EquipmentService`.
 */
@Component
class DualWield : PassiveSkillScript {

  override val skill = SkillId.M_DUAL_WIELD

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    if (context.offHandWeaponType?.isDualWieldable != true) return

    context.offHandDamagePercent = offHandDamagePercent(level)
    context.addPercent(aspd = -aspdPenaltyPercent(level))
  }

  companion object {
    private const val MAX_LEVEL = 5

    fun offHandDamagePercent(level: Int): Int {
      return 30 + 10 * (level.coerceIn(1, MAX_LEVEL) - 1)
    }

    fun aspdPenaltyPercent(level: Int): Int {
      return 25 - 5 * (level.coerceIn(1, MAX_LEVEL) - 1)
    }
  }
}
