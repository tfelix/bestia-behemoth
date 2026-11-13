package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/**
 * Vulture's Eye (`skills.yml` id 154): with a weapon that shoots or is thrown in hand, `+1/lv` tile of range and
 * `+2/lv` HIT. A thrown weapon has no range of its own, so its tiles go to the throw, see `Javelin`.
 */
@Component
class VulturesEye : PassiveSkillScript {

  override val skill = SkillId.M_VULTURES_EYE

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    val shooter = context.rangedWeapon
    val throws = context.heldWeaponType == WeaponType.THROWN
    if (shooter == null && !throws) return

    context.rangedWeapon = shooter?.copy(range = shooter.range + TILES_PER_LEVEL * level)
    if (throws) {
      context.throwReachTiles += TILES_PER_LEVEL * level
    }
    context.addAccuracy(hit = HIT_PER_LEVEL * level)
  }

  private companion object {
    const val TILES_PER_LEVEL = 1
    const val HIT_PER_LEVEL = 2
  }
}
