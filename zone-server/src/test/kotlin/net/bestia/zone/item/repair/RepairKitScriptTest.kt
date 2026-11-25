package net.bestia.zone.item.repair

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.gear.ArmorType
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.script.ScriptArgKeys
import net.bestia.zone.script.ScriptArgs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RepairKitScriptTest {

  private val world = testWorld()

  private val templates = mockk<ItemTemplateRegistry> {
    every { templateOf(SWORD) } returns template(SWORD, weaponType = WeaponType.SWORD)
    every { templateOf(CUIRASS) } returns template(CUIRASS, armorType = ArmorType.HEAVY)
    every { templateOf(TORCH) } returns template(TORCH, weaponType = WeaponType.SWORD, stackable = true)
  }

  private val inventoryService = mockk<InventoryService>(relaxed = true)

  private val asyncJobExecutor = mockk<AsyncJobExecutor> {
    every { submit(any<Long>(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }

  private val weaponKit = RepairKitScript(RepairKit.WEAPON, templates, inventoryService, asyncJobExecutor)
  private val armorKit = RepairKitScript(RepairKit.ARMOR, templates, inventoryService, asyncJobExecutor)

  @Test
  fun `a weapon kit restores the full durability of a held weapon`() {
    val user = givenUser(item(SWORD, uniqueId = 7, durability = 12))

    assertTrue(weaponKit.execute(world, user, targeting(7)))

    assertEquals(MAX_DURABILITY, world.getOrThrow(user, Inventory::class).byPlayerItemId(7)!!.durability)
    verify { inventoryService.repair(MASTER_ID, 7, null) }
  }

  @Test
  fun `each kit mends only its own kind of gear`() {
    val user = givenUser(item(SWORD, uniqueId = 7, durability = 12), item(CUIRASS, uniqueId = 8, durability = 12))

    assertFalse(weaponKit.execute(world, user, targeting(8)))
    assertFalse(armorKit.execute(world, user, targeting(7)))
    assertTrue(armorKit.execute(world, user, targeting(8)))
  }

  @Test
  fun `undamaged gear is refused, so the kit is not spent for nothing`() {
    val user = givenUser(item(SWORD, uniqueId = 7, durability = MAX_DURABILITY))

    assertFalse(weaponKit.execute(world, user, targeting(7)))
    verify(exactly = 0) { inventoryService.repair(any(), any(), any()) }
  }

  @Test
  fun `a broken weapon is mended too`() {
    val user = givenUser(item(SWORD, uniqueId = 7, durability = 0))

    assertTrue(weaponKit.execute(world, user, targeting(7)))
  }

  @Test
  fun `stackable gear is refused, as at the forge`() {
    val user = givenUser(item(TORCH, uniqueId = 7, durability = 12))

    assertFalse(weaponKit.execute(world, user, targeting(7)))
  }

  @Test
  fun `worn armour is mended in its slot and its wearer's stats are worked out again`() {
    // The bag still shows the wear from before it was put on; the slot knows better.
    val user = givenUser(item(CUIRASS, uniqueId = 8, durability = MAX_DURABILITY).apply { equipped = true })
    val equipment = Equipment(availableSlotMask = EquipmentSlots.ALL).apply {
      equip(EquipmentSlot.ARMOR, Equipment.EquippedItem(itemId = CUIRASS, uniqueId = 8, durability = 30, maxDurability = MAX_DURABILITY))
    }
    world.add(user, equipment)

    assertTrue(armorKit.execute(world, user, targeting(8)))

    assertEquals(MAX_DURABILITY, equipment.get(EquipmentSlot.ARMOR)!!.durability)
    assertTrue(world.has(user, IsStatusValueDirty::class))
    verify { inventoryService.repair(MASTER_ID, 8, null) }
  }

  private fun givenUser(vararg items: Inventory.Item) = world.createEntity { id ->
    add(id, Master(masterId = MASTER_ID))
    add(id, Inventory(items.toMutableList()))
  }

  private fun targeting(playerItemId: Long) = ScriptArgs.of(ScriptArgKeys.TARGET_ITEM to playerItemId)

  private fun item(itemId: Long, uniqueId: Long, durability: Int) = Inventory.Item(
    itemId = itemId, amount = 1, weight = 1, uniqueId = uniqueId, stackable = false, durability = durability,
    maxDurability = MAX_DURABILITY
  )

  private fun template(
    id: Long,
    weaponType: WeaponType? = null,
    armorType: ArmorType? = null,
    stackable: Boolean = false,
  ) = ItemTemplateRegistry.Template(
    id = id, baseLevel = 1, weight = 1, stackable = stackable, maxDurability = MAX_DURABILITY,
    material = ItemMaterial.METAL, weaponType = weaponType, armorType = armorType
  )

  private companion object {
    const val MASTER_ID = 42L
    const val SWORD = 18L
    const val CUIRASS = 22L
    const val TORCH = 300L
    const val MAX_DURABILITY = 100
  }
}
