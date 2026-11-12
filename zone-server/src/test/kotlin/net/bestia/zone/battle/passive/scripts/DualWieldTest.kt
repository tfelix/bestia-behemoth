package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DualWieldTest {

  private val base = StatusValues(1, 1, 1, 1, 1, 1)
  private val context = StatusValueRecalcContext(base, baseSpeed = 10f)

  @Test
  fun `a second weapon strikes at 30 to 70 percent and slows the swings by 25 to 5 percent`() {
    context.offHandWeaponType = WeaponType.DAGGER

    DualWield().apply(context, 2)

    assertEquals(40, context.offHandDamagePercent)
    assertEquals(-20, context.aspdPercent)
    assertEquals(listOf(30, 40, 50, 60, 70), (1..5).map { DualWield.offHandDamagePercent(it) })
    assertEquals(listOf(25, 20, 15, 10, 5), (1..5).map { DualWield.aspdPenaltyPercent(it) })
  }

  @Test
  fun `a shield or a tome in the left hand costs nothing and swings nothing`() {
    context.offHandWeaponType = WeaponType.TOME

    DualWield().apply(context, 5)

    assertEquals(0, context.offHandDamagePercent)
    assertEquals(0, context.aspdPercent)
    assertNull(CombatValues.fromContext(1, base, context).offHandWeapon)
  }

  @Test
  fun `the second weapon's ATK becomes a weapon of its own`() {
    context.offHandWeaponType = WeaponType.DAGGER
    context.bookAttackToOffHand { context.addAttack(atk = 12) }
    DualWield().apply(context, 1)

    val combat = CombatValues.fromContext(1, base, context)

    assertEquals(12, combat.offHandWeapon?.atk)
    assertEquals(0, combat.weapon.atk)
    assertEquals(30, combat.offHandDamagePercent)
  }
}
