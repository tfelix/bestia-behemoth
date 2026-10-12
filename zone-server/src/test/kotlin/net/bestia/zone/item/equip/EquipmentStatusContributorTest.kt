package net.bestia.zone.item.equip

import net.bestia.zone.battle.ecs.status.BaseStatusValues
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.equip.script.EquipmentScriptRegistry
import net.bestia.zone.item.equip.script.IronSwordScript
import net.bestia.zone.item.equip.script.StatBonusEquipmentScript
import net.bestia.zone.item.stat.ItemStat
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.item.stat.ItemStatSheet
import net.bestia.zone.item.stat.StatValue
import kotlin.test.Test
import kotlin.test.assertEquals

class EquipmentStatusContributorTest {

  private val catalog = ItemStatCatalog()
  private val sut = EquipmentStatusContributor(
    EquipmentScriptRegistry(listOf(StatBonusEquipmentScript(), IronSwordScript())),
    catalog
  )
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

  @Test
  fun `an item without a script still brings its base properties and stats`() {
    catalog.load(mapOf(1L to ItemStatSheet(attack = 10, stats = mapOf(ItemStat.STR to 3, ItemStat.HIT to 5))))
    val fighter = wearing(EquipmentSlot.RIGHT_HAND to 0)

    sut.contribute(context, world, fighter)

    assertEquals(10, context.attack)
    assertEquals(1 + 3, context.strength)
    assertEquals(5, context.hit)
  }

  @Test
  fun `a forged copy adds what it rolled on top of its fixed stats`() {
    catalog.load(mapOf(1L to ItemStatSheet(stats = mapOf(ItemStat.STR to 3))))
    val equipment = Equipment(availableSlotMask = EquipmentSlots.ALL)
    equipment.equip(
      EquipmentSlot.ARMOR,
      Equipment.EquippedItem(itemId = 1, uniqueId = 5L, rolledStats = listOf(StatValue(ItemStat.STR, 2)))
    )
    val fighter = world.createEntity { id -> add(id, equipment) }

    sut.contribute(context, world, fighter)

    assertEquals(1 + 3 + 2, context.strength)
  }

  private fun wearing(vararg items: Pair<EquipmentSlot, Int>): Long {
    val equipment = Equipment(availableSlotMask = EquipmentSlots.ALL)
    items.forEachIndexed { index, (slot, upgradeLevel) ->
      equipment.equip(slot, Equipment.EquippedItem(itemId = 1, uniqueId = index + 1L, upgradeLevel = upgradeLevel))
    }

    return world.createEntity { id -> add(id, equipment) }
  }
}
