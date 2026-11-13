package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.RangedWeapon
import net.bestia.zone.battle.ammo.AmmoType
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HunterPassivesTest {

  @Test
  fun `Camouflage cuts the range the wild notices the master at by a tenth per level`() {
    assertEquals(listOf(10, 20, 30), (1..3).map { level -> recalc { Camouflage().apply(it, level) }.noticeRangeCutPercent })
  }

  @Test
  fun `Trailblazer quickens the walk 3 percent per level off the roads only`() {
    val offRoad = recalc { it.walksOffRoad = true; Trailblazer().apply(it, 5) }
    val onRoad = recalc { Trailblazer().apply(it, 5) }

    assertEquals(11.5f, offRoad.speed, 0.001f)
    assertEquals(10f, onRoad.speed)
    assertEquals(15, onRoad.offRoadSpeedPercent, "the ground is watched on the road too")
  }

  @Test
  fun `Pack Hunt makes the bestias 10 percent a level fiercer against the prey, and not the master`() {
    assertEquals(30, recalc { PackHunt().applyToBestia(it, 3) }.markedPreyDamagePercent)
    assertEquals(0, recalc { PackHunt().apply(it, 3) }.markedPreyDamagePercent)
    assertTrue(PackHunt().reachesOwnedBestias)
  }

  @Test
  fun `Owl's Eye sharpens DEX by one a level`() {
    assertEquals(1 + 5, recalc { OwlsEye().apply(it, 5) }.dexterity)
  }

  @Test
  fun `Vulture's Eye lengthens the bow by a tile and adds 2 HIT a level`() {
    val archer = recalc { it.rangedWeapon = BOW; VulturesEye().apply(it, 5) }

    assertEquals(BOW.range + 5, archer.rangedWeapon?.range)
    assertEquals(10, archer.hit)
    assertEquals(0, archer.throwReachTiles, "a bow is not thrown")
  }

  @Test
  fun `Vulture's Eye lets a thrown weapon reach farther and hit surer`() {
    val thrower = recalc { it.heldWeaponType = WeaponType.THROWN; VulturesEye().apply(it, 3) }

    assertEquals(3, thrower.throwReachTiles)
    assertEquals(6, thrower.hit)
  }

  @Test
  fun `Vulture's Eye does nothing for a weapon that neither shoots nor is thrown`() {
    val swordsman = recalc { it.heldWeaponType = WeaponType.SPEAR; VulturesEye().apply(it, 5) }

    assertEquals(0, swordsman.hit)
    assertEquals(0, swordsman.throwReachTiles)
  }

  private fun recalc(apply: (StatusValueRecalcContext) -> Unit): StatusValueRecalcContext {
    return StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f).also(apply)
  }

  private companion object {
    val BOW = RangedWeapon(range = 12, ammoType = AmmoType.ARROW)
  }
}
