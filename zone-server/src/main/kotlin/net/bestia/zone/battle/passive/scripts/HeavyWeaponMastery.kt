package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** Heavy Weapon Mastery (`skills.yml` id 132): `+3/lv` ATK while the right hand holds one of the Knight's weapons. */
@Component
class HeavyWeaponMastery : PassiveSkillScript {

  override val skill = SkillId.M_HEAVY_WEAPON_MASTERY

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    if (context.heldWeaponType !in KNIGHT_WEAPONS) return

    context.addAttack(atk = ATK_PER_LEVEL * level)
  }

  companion object {
    private const val ATK_PER_LEVEL = 3

    /** The Knight profession's weapons. Battle may not name a profession, so a test keeps the two equal. */
    val KNIGHT_WEAPONS: Set<WeaponType> = setOf(
      WeaponType.SWORD, WeaponType.GREATSWORD, WeaponType.AXE, WeaponType.GREATAXE,
      WeaponType.MACE, WeaponType.HAMMER, WeaponType.SPEAR, WeaponType.POLEARM,
    )
  }
}
