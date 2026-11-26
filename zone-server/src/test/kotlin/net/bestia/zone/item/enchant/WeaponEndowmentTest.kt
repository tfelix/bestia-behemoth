package net.bestia.zone.item.enchant

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.itemTemplatesOf
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.persistence.ItemInstance
import net.bestia.zone.item.persistence.ItemInstanceRepository
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.function.Consumer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WeaponEndowmentTest {

  private val now = Instant.parse("2026-11-26T12:00:00Z")
  private val sword = Item(
    id = 10L, identifier = "sword", weight = 10, type = Item.ItemType.EQUIP, equipSlot = EquipmentSlot.RIGHT_HAND,
    weaponType = WeaponType.SWORD, material = ItemMaterial.METAL
  )
  private val shield = Item(
    id = 11L, identifier = "shield", weight = 10, type = Item.ItemType.EQUIP, equipSlot = EquipmentSlot.RIGHT_HAND,
    material = ItemMaterial.METAL
  )
  private val swordRow = ItemInstance(item = sword, id = 77L)
  private val instances = mockk<ItemInstanceRepository> { every { findById(77L) } returns Optional.of(swordRow) }
  private val jobs = mockk<AsyncJobExecutor> {
    every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }
  private val transactions = mockk<TransactionTemplate> {
    every { executeWithoutResult(any()) } answers {
      firstArg<Consumer<TransactionStatus>>().accept(mockk(relaxed = true))
    }
  }
  private val sut = WeaponEndowment(
    itemTemplatesOf(sword, shield), instances, jobs, transactions, Clock.fixed(now, ZoneOffset.UTC)
  )
  private val world = testWorld()

  @Test
  fun `the endow binds to the weapon in hand, in the slot, the bag and the row`() {
    val wielder = holding(sword)

    assertTrue(sut.endow(world, wielder, Element.FIRE, level = 2, potency = 64))

    val expected = ActiveItemEnchantment(
      ItemEnchantmentId.ENDOWED_WEAPON, strength = 64, value = Element.FIRE.ordinal,
      endsAt = now.plusSeconds(120), level = 2
    )
    assertEquals(listOf(expected), world.get(wielder, Equipment::class)!!.get(EquipmentSlot.RIGHT_HAND)!!.enchantments)
    assertEquals(listOf(expected), world.get(wielder, Inventory::class)!!.byPlayerItemId(77L)!!.enchantments)
    assertEquals(listOf(expected), swordRow.enchantments(now))
    assertNotNull(world.get(wielder, IsStatusValueDirty::class))
  }

  @Test
  fun `a new endow replaces the element the weapon held`() {
    val wielder = holding(sword)

    sut.endow(world, wielder, Element.FIRE, level = 1, potency = 64)
    sut.endow(world, wielder, Element.WATER, level = 1, potency = 64)

    val endows = world.get(wielder, Equipment::class)!!.get(EquipmentSlot.RIGHT_HAND)!!.enchantments
    assertEquals(listOf(Element.WATER.ordinal), endows.map { it.value })
  }

  @Test
  fun `without a weapon in hand there is nothing to bind to`() {
    assertFalse(sut.endow(world, holding(shield), Element.FIRE, level = 1, potency = 64))
  }

  @Test
  fun `lifting takes the endow off the weapon and its row`() {
    val wielder = holding(sword)
    sut.endow(world, wielder, Element.FIRE, level = 1, potency = 64)

    sut.lift(world, wielder)

    assertEquals(emptyList(), world.get(wielder, Equipment::class)!!.get(EquipmentSlot.RIGHT_HAND)!!.enchantments)
    assertEquals(emptyList(), world.get(wielder, Inventory::class)!!.byPlayerItemId(77L)!!.enchantments)
    assertEquals(emptyList(), swordRow.enchantments(now))
  }

  private fun holding(item: Item): EntityId {
    val equipment = Equipment(availableSlotMask = EquipmentSlots.ALL)
    equipment.equip(EquipmentSlot.RIGHT_HAND, Equipment.EquippedItem(itemId = item.id, uniqueId = 77L))
    val bag = Inventory(
      mutableListOf(Inventory.Item(itemId = item.id, amount = 1, uniqueId = 77L, stackable = false, equipped = true))
    )

    return world.createEntity { id ->
      add(id, Master(masterId = 5L))
      add(id, equipment)
      add(id, bag)
    }
  }
}
