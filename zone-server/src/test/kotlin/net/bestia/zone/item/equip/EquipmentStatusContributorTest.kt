package net.bestia.zone.item.equip

import net.bestia.zone.battle.ecs.status.BaseStatusValues
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.equip.script.EquipmentScriptRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class EquipmentStatusContributorTest {

  private val sut = EquipmentStatusContributor(EquipmentScriptRegistry(emptyList()))
  private val world = testWorld()
  private val context = StatusValueRecalcContext(BaseStatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 1f)

  @Test
  fun `the weapon in the right hand brings its refinement`() {
    val fighter = wearing(EquipmentSlot.RIGHT_HAND to 4, EquipmentSlot.LEFT_HAND to 9)

    sut.contribute(context, world, fighter)

    assertEquals(4, context.weaponUpgradeLevel)
  }

  @Test
  fun `bare hands have no refinement`() {
    val fighter = wearing(EquipmentSlot.LEFT_HAND to 9)

    sut.contribute(context, world, fighter)

    assertEquals(0, context.weaponUpgradeLevel)
  }

  private fun wearing(vararg items: Pair<EquipmentSlot, Int>): Long {
    val equipment = Equipment(availableSlotMask = EquipmentSlots.ALL)
    items.forEachIndexed { index, (slot, upgradeLevel) ->
      equipment.equip(slot, Equipment.EquippedItem(itemId = 1, uniqueId = index + 1L, upgradeLevel = upgradeLevel))
    }

    return world.createEntity { id -> add(id, equipment) }
  }
}
