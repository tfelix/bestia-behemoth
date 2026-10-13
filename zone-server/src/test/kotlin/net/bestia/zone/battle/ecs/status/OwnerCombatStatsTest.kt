package net.bestia.zone.battle.ecs.status

import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OwnerCombatStatsTest {

  // STR 6, AGI 6, VIT 9, INT 8, DEX 10, WIL 13.
  private val base = StatusValues(
    strength = 6, vitality = 9, intelligence = 8, agility = 6, willpower = 13, dexterity = 10
  )

  private val bare = CombatValues.ofBase(level = 20, base = base)

  private fun geared(): CombatValues {
    val context = StatusValueRecalcContext(base, baseSpeed = 1f)
    context.addAttack(atk = 10, matk = 7)
    context.addDefense(hardDefense = 4)
    context.weaponUpgradeLevel = 3
    return CombatValues.fromContext(level = 20, base = base, context = context)
  }

  @Test
  fun `the message carries what the damage path reads, weapon refinement included`() {
    val stats = OwnerCombatStats(geared())

    val msg = stats.toEntityMessage(entityId = 5L) as CombatStatsComponentSMSG

    assertEquals(5L, msg.entityId)
    assertEquals(bare.derived.atk, msg.atk)
    assertEquals(16, msg.weaponAtk)
    assertEquals(bare.derived.matk, msg.matk)
    assertEquals(7, msg.weaponMatk)
    assertEquals(bare.derived.hitrate, msg.hit)
    assertEquals(bare.derived.flee, msg.flee)
    assertEquals(bare.derived.crit, msg.crit)
    assertEquals(bare.defense.defense, msg.softDefense)
    assertEquals(4, msg.hardDefense)
  }

  @Test
  fun `ASPD reads the bare-handed swing of AGI 6 and DEX 10 as 132`() {
    // 700 ms motion, shortened by (4 * AGI + DEX) / 1000 = 3.4 %: 677 ms.
    assertEquals(132, OwnerCombatStats(bare).aspd)
  }

  @Test
  fun `an equal block sends nothing`() {
    val stats = OwnerCombatStats(bare)
    stats.clearDirty()

    stats.update(CombatValues.ofBase(level = 20, base = base))

    assertFalse(stats.isDirty())
  }

  @Test
  fun `a changed block is taken over and sent`() {
    val stats = OwnerCombatStats(bare)
    stats.clearDirty()

    stats.update(geared())

    assertEquals(geared(), stats.values)
    assertTrue(stats.isDirty())
  }
}
