package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.gear.ArmorType
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.item.profession.Profession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BardPassivesTest {

  @Test
  fun `Instrument Mastery adds 2 ATK a level with an instrument or a whip`() {
    fun masteryAtk(weapon: WeaponType?) = recalc {
      it.heldWeaponType = weapon
      InstrumentMastery().apply(it, 10)
    }.attack

    assertEquals(20, masteryAtk(WeaponType.INSTRUMENT))
    assertEquals(20, masteryAtk(WeaponType.WHIP))
    assertEquals(0, masteryAtk(WeaponType.BOW))
    assertEquals(0, masteryAtk(null))
  }

  @Test
  fun `Instrument Mastery counts only weapons a Bard may hold`() {
    assertTrue(Profession.BARD.weapons.containsAll(InstrumentMastery.BARD_WEAPONS))
  }

  @Test
  fun `Nimble Steps adds 2 FLEE a level in light armor, cloth or none`() {
    fun nimbleFlee(body: ArmorType?) = recalc {
      it.bodyArmorType = body
      NimbleSteps().apply(it, 5)
    }.flee

    assertEquals(10, nimbleFlee(null))
    assertEquals(10, nimbleFlee(ArmorType.CLOTH))
    assertEquals(10, nimbleFlee(ArmorType.LIGHT))
    assertEquals(0, nimbleFlee(ArmorType.MEDIUM))
    assertEquals(0, nimbleFlee(ArmorType.HEAVY))
  }

  private fun recalc(apply: (StatusValueRecalcContext) -> Unit): StatusValueRecalcContext {
    return StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f).also(apply)
  }
}
