package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.damage.HitForm
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WarriorTrunkPassivesTest {

  private val context = StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f)

  @Test
  fun `Endure keeps HP and mana regenerating in combat`() {
    Endure().apply(context, 1)

    assertTrue(context.hpRegen.inCombat)
    assertTrue(context.manaRegen.inCombat)
  }

  @Test
  fun `Iron Skin takes three percent per level off physical hits only`() {
    IronSkin().apply(context, 5)

    assertEquals(mapOf(HitForm.PHYSICAL to 15), context.damageModifiers.takenByForm)
  }
}
