package net.bestia.zone.item.equip

import net.bestia.zone.item.ecs.itemTemplatesOf
import net.bestia.zone.battle.ecs.status.BaseStatusValues
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.equip.script.EquipmentScriptRegistry
import net.bestia.zone.item.equip.script.IronSwordScript
import net.bestia.zone.item.equip.script.StatBonusEquipmentScript
import net.bestia.zone.item.essence.essenceStatsOf
import net.bestia.zone.item.stat.ItemStat
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.item.stat.ItemStatSheet
import net.bestia.zone.item.stat.StatValue
import net.bestia.zone.battle.gear.ArmorType
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.item.persistence.Item
import kotlin.test.Test
import kotlin.test.assertEquals
import net.bestia.zone.item.enchant.itemEnchantmentScriptsOf
import net.bestia.zone.battle.Element
import net.bestia.zone.item.enchant.ActiveItemEnchantment
import net.bestia.zone.item.enchant.EndowedWeapon
import net.bestia.zone.item.enchant.ItemEnchantmentId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertNull

class EquipmentStatusContributorTest {

  private val catalog = ItemStatCatalog()
  private val sut = EquipmentStatusContributor(
    EquipmentScriptRegistry(listOf(StatBonusEquipmentScript(), IronSwordScript())),
    catalog,
    itemTemplatesOf(),
    essenceStatsOf(),
    itemEnchantmentScriptsOf()
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

  @Test
  fun `a second weapon's ATK is kept apart from the main weapon's`() {
    val dagger = Item(
      id = 2L, identifier = "dagger", weight = 10, type = Item.ItemType.EQUIP, equipSlot = EquipmentSlot.RIGHT_HAND,
      weaponType = WeaponType.DAGGER, material = ItemMaterial.METAL
    )
    val contributor = EquipmentStatusContributor(
      EquipmentScriptRegistry(listOf(StatBonusEquipmentScript())), catalog, itemTemplatesOf(dagger), essenceStatsOf(),
      itemEnchantmentScriptsOf()
    )
    catalog.load(mapOf(1L to ItemStatSheet(attack = 20), dagger.id to ItemStatSheet(attack = 7)))
    val equipment = Equipment(availableSlotMask = EquipmentSlots.ALL)
    equipment.equip(EquipmentSlot.RIGHT_HAND, Equipment.EquippedItem(itemId = 1, uniqueId = 1L))
    equipment.equip(EquipmentSlot.LEFT_HAND, Equipment.EquippedItem(itemId = dagger.id, uniqueId = 2L, upgradeLevel = 3))
    val duelist = world.createEntity { id -> add(id, equipment) }

    contributor.contribute(context, world, duelist)

    assertEquals(20, context.attack)
    assertEquals(7, context.offHandAttack)
    assertEquals(3, context.offHandUpgradeLevel)
  }

  @Test
  fun `every worn piece with a weight is counted, the shield too`() {
    fun piece(id: Long, slot: EquipmentSlot, type: ArmorType): Item {
      return Item(id = id, identifier = slot.name.lowercase(), weight = 10, type = Item.ItemType.EQUIP, equipSlot = slot, armorType = type, material = ItemMaterial.METAL)
    }
    val cuirass = piece(2L, EquipmentSlot.ARMOR, ArmorType.HEAVY)
    val shield = piece(3L, EquipmentSlot.LEFT_HAND, ArmorType.HEAVY)
    val boots = piece(4L, EquipmentSlot.FOOTGEAR, ArmorType.LIGHT)
    val contributor = EquipmentStatusContributor(
      EquipmentScriptRegistry(listOf(StatBonusEquipmentScript())), catalog, itemTemplatesOf(cuirass, shield, boots),
      essenceStatsOf(), itemEnchantmentScriptsOf()
    )
    val equipment = Equipment(availableSlotMask = EquipmentSlots.ALL)
    listOf(cuirass, shield, boots).forEach { equipment.equip(it.equipSlot!!, Equipment.EquippedItem(itemId = it.id, uniqueId = it.id)) }
    equipment.equip(EquipmentSlot.RIGHT_HAND, Equipment.EquippedItem(itemId = 1, uniqueId = 1L))
    val knight = world.createEntity { id -> add(id, equipment) }

    contributor.contribute(context, world, knight)

    assertEquals(listOf(ArmorType.LIGHT, ArmorType.HEAVY, ArmorType.HEAVY), context.wornArmorTypes.sorted())
  }

  @Test
  fun `an endowed weapon in hand strikes as its element and harder per level`() {
    val fighter = wieldingEndowed(EquipmentSlot.RIGHT_HAND, endsAt = Instant.now().plusSeconds(60))

    endowAware().contribute(context, world, fighter)

    assertEquals(Element.FIRE, context.weaponElement)
    assertEquals(10, context.attackPercent)
  }

  @Test
  fun `an ended endow does nothing`() {
    val fighter = wieldingEndowed(EquipmentSlot.RIGHT_HAND, endsAt = Instant.now().minusSeconds(1))

    endowAware().contribute(context, world, fighter)

    assertNull(context.weaponElement)
  }

  @Test
  fun `an endowed second blade does not lend its element`() {
    val fighter = wieldingEndowed(EquipmentSlot.LEFT_HAND, endsAt = Instant.now().plusSeconds(60))

    endowAware().contribute(context, world, fighter)

    assertNull(context.weaponElement)
  }

  private fun endowAware(): EquipmentStatusContributor {
    return EquipmentStatusContributor(
      EquipmentScriptRegistry(emptyList()), catalog, itemTemplatesOf(), essenceStatsOf(),
      itemEnchantmentScriptsOf(EndowedWeapon())
    )
  }

  private fun wieldingEndowed(slot: EquipmentSlot, endsAt: Instant): Long {
    val endow = ActiveItemEnchantment(
      ItemEnchantmentId.ENDOWED_WEAPON, strength = 60, value = Element.FIRE.ordinal, endsAt = endsAt, level = 2
    )
    val equipment = Equipment(availableSlotMask = EquipmentSlots.ALL)
    equipment.equip(slot, Equipment.EquippedItem(itemId = 1, uniqueId = 1L, enchantments = listOf(endow)))

    return world.createEntity { id -> add(id, equipment) }
  }

  private fun wearing(vararg items: Pair<EquipmentSlot, Int>): Long {
    val equipment = Equipment(availableSlotMask = EquipmentSlots.ALL)
    items.forEachIndexed { index, (slot, upgradeLevel) ->
      equipment.equip(slot, Equipment.EquippedItem(itemId = 1, uniqueId = index + 1L, upgradeLevel = upgradeLevel))
    }

    return world.createEntity { id -> add(id, equipment) }
  }
}
