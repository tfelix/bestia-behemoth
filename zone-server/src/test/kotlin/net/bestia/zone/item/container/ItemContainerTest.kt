package net.bestia.zone.item.container

import net.bestia.zone.item.Item
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.instance.ItemInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ItemContainerTest {

  private val apple = Item(id = 1L, identifier = "apple", weight = 2, type = Item.ItemType.ETC)
  private val potion = Item(id = 2L, identifier = "potion", weight = 3, type = Item.ItemType.USABLE, script = "NoopScript")
  private val sword = Item(
    id = 3L, identifier = "sword", weight = 10, type = Item.ItemType.EQUIP,
    equipSlot = EquipmentSlot.RIGHT_HAND
  )
  private val boots = Item(
    id = 4L, identifier = "boots", weight = 8, type = Item.ItemType.EQUIP,
    equipSlot = EquipmentSlot.FOOTGEAR
  )

  private lateinit var container: ItemContainer

  @BeforeEach
  fun setUp() {
    container = ItemContainer(ItemContainer.Type.MASTER)
  }

  @Test
  fun `stackable items of the same template merge into a single slot`() {
    container.addStackable(apple, 5)
    container.addStackable(apple, 7)

    assertEquals(1, container.slots.size)
    assertEquals(12, container.slots.single().amount)
  }

  @Test
  fun `stackable items of different templates get their own slots`() {
    container.addStackable(apple, 5)
    container.addStackable(potion, 2)

    assertEquals(2, container.slots.size)
  }

  @Test
  fun `instances never merge, not even of the same template`() {
    container.addInstance(ItemInstance(item = sword))
    container.addInstance(ItemInstance(item = sword))

    assertEquals(2, container.slots.size)
    assertTrue(container.slots.all { it.amount == 1 && !it.isStackable })
  }

  @Test
  fun `an instance and a plain stack of the same template stay separate`() {
    container.addStackable(potion, 3)
    container.addInstance(ItemInstance(item = potion))

    assertEquals(2, container.slots.size)
    assertEquals(1, container.slots.count { it.isStackable })
    assertEquals(1, container.slots.count { !it.isStackable })
  }

  @Test
  fun `removeStackable reduces the amount and removes the slot when it hits zero`() {
    container.addStackable(apple, 5)

    assertTrue(container.removeStackable(apple.id, 2))
    assertEquals(3, container.slots.single().amount)

    assertTrue(container.removeStackable(apple.id, 3))
    assertTrue(container.slots.isEmpty())
  }

  @Test
  fun `removeStackable fails when not enough is present`() {
    container.addStackable(apple, 1)

    assertFalse(container.removeStackable(apple.id, 5))
    assertEquals(1, container.slots.single().amount)
  }

  @Test
  fun `removeOne prefers a unique instance and hands it back so its identity is preserved`() {
    container.addStackable(apple, 1) // a plain apple stack
    val instance = ItemInstance(item = sword)
    container.addInstance(instance)
    container.addStackable(potion, 1) // unrelated potion stack

    val removed = container.removeOne(sword.id, 1)

    assertSame(instance, removed?.instance)
    assertTrue(container.slots.none { !it.isStackable }) // instance slot gone
    assertEquals(2, container.slots.size) // the two stackable slots remain
  }

  @Test
  fun `removeOne on a plain stackable returns no instance`() {
    container.addStackable(apple, 3)

    val removed = container.removeOne(apple.id, 2)

    assertNull(removed?.instance)
    assertEquals(0L, removed?.uniqueId)
    assertEquals(1, container.slots.single().amount)
  }

  @Test
  fun `removeOne returns null when the item is not present`() {
    assertNull(container.removeOne(apple.id, 1))
  }

  @Test
  fun `hasItem counts both plain stacks and instances of a template`() {
    container.addStackable(apple, 4)
    container.addInstance(ItemInstance(item = sword))

    assertTrue(container.hasItem(apple.id, 4))
    assertFalse(container.hasItem(apple.id, 5))
    assertTrue(container.hasItem(sword.id, 1))
  }

  @Test
  fun `equip marks the holding slot and unequip clears it again`() {
    container.addInstance(ItemInstance(item = sword))

    assertTrue(container.equip(sword.id, uniqueId = 0L, slot = EquipmentSlot.RIGHT_HAND))
    assertEquals(EquipmentSlot.RIGHT_HAND, container.slots.single().equippedIn)
    assertEquals(setOf(EquipmentSlot.RIGHT_HAND), container.equipped().keys)

    assertSame(container.slots.single(), container.unequip(EquipmentSlot.RIGHT_HAND))
    assertNull(container.slots.single().equippedIn)
    assertTrue(container.equipped().isEmpty())
  }

  @Test
  fun `equip refuses a slot that is already occupied`() {
    container.addInstance(ItemInstance(item = sword))
    container.addInstance(ItemInstance(item = sword))

    assertTrue(container.equip(sword.id, 0L, EquipmentSlot.RIGHT_HAND))
    assertFalse(container.equip(sword.id, 0L, EquipmentSlot.RIGHT_HAND))
    assertEquals(1, container.equipped().size)
  }

  @Test
  fun `equip refuses an item that belongs in a different slot`() {
    container.addInstance(ItemInstance(item = boots))

    assertFalse(container.equip(boots.id, 0L, EquipmentSlot.RIGHT_HAND))
    assertTrue(container.equipped().isEmpty())
  }

  @Test
  fun `equip refuses a non-equipment item`() {
    container.addInstance(ItemInstance(item = potion))

    assertFalse(container.equip(potion.id, 0L, EquipmentSlot.RIGHT_HAND))
    assertTrue(container.equipped().isEmpty())
  }

  @Test
  fun `equip refuses an item that is not held at all`() {
    assertFalse(container.equip(sword.id, 0L, EquipmentSlot.RIGHT_HAND))
  }

  @Test
  fun `unequip returns null for an empty slot`() {
    assertNull(container.unequip(EquipmentSlot.ARMOR))
  }

  @Test
  fun `worn items can not be removed - you have to take them off first`() {
    container.addInstance(ItemInstance(item = sword))
    container.equip(sword.id, 0L, EquipmentSlot.RIGHT_HAND)

    assertNull(container.removeOne(sword.id, 1))
    assertEquals(1, container.slots.size)

    container.unequip(EquipmentSlot.RIGHT_HAND)
    assertNotNull(container.removeOne(sword.id, 1))
    assertTrue(container.slots.isEmpty())
  }

  @Test
  fun `equipping a second copy picks a not-yet-worn one`() {
    container.addInstance(ItemInstance(item = sword))
    container.addInstance(ItemInstance(item = boots))

    assertTrue(container.equip(sword.id, 0L, EquipmentSlot.RIGHT_HAND))
    assertTrue(container.equip(boots.id, 0L, EquipmentSlot.FOOTGEAR))

    assertEquals(2, container.equipped().size)
    assertEquals(sword.id, container.equipped()[EquipmentSlot.RIGHT_HAND]?.template?.id)
    assertEquals(boots.id, container.equipped()[EquipmentSlot.FOOTGEAR]?.template?.id)
  }
}
