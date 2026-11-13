package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** Instrument Mastery (`skills.yml` id 160): `+2/lv` ATK while the right hand holds an instrument or a whip. */
@Component
class InstrumentMastery : PassiveSkillScript {

  override val skill = SkillId.M_INSTRUMENT_MASTERY

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    if (context.heldWeaponType !in BARD_WEAPONS) return

    context.addAttack(atk = ATK_PER_LEVEL * level)
  }

  companion object {
    private const val ATK_PER_LEVEL = 2

    /** A bow is a Bard's weapon too, but no instrument: it gains nothing here. */
    val BARD_WEAPONS: Set<WeaponType> = setOf(WeaponType.INSTRUMENT, WeaponType.WHIP)
  }
}
