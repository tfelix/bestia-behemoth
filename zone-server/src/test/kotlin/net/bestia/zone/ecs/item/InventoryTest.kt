package net.bestia.zone.ecs.item

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InventoryTest {
  private lateinit var inventory: Inventory

  @BeforeEach
  fun setup() {
    inventory = Inventory(mutableListOf())
  }

  // Dirty flag initial state
  @Test
  fun `inventory starts dirty`() {
    assertTrue(inventory.isDirty())
  }

  // addItem marks dirty
  @Test
  fun `addItem marks dirty`() {
    inventory.clearDirty()
    assertFalse(inventory.isDirty())

    inventory.addItem(Inventory.Item(itemId = 1, amount = 10))

    assertTrue(inventory.isDirty())
  }

  @Test
  fun `addItem stacks stackable items and marks dirty`() {
    inventory.clearDirty()

    inventory.addItem(Inventory.Item(itemId = 1, amount = 10, uniqueId = 0))
    assertTrue(inventory.isDirty())

    inventory.clearDirty()
    inventory.addItem(Inventory.Item(itemId = 1, amount = 5, uniqueId = 0))

    assertTrue(inventory.isDirty())
    assertEquals(1, inventory.size())
    assertEquals(15, inventory.getItem(1)?.amount)
  }

  @Test
  fun `addItem does not stack unique items and marks dirty`() {
    inventory.clearDirty()

    inventory.addItem(Inventory.Item(itemId = 1, amount = 1, uniqueId = 123))
    assertTrue(inventory.isDirty())

    inventory.clearDirty()
    inventory.addItem(Inventory.Item(itemId = 1, amount = 1, uniqueId = 456))

    assertTrue(inventory.isDirty())
    assertEquals(2, inventory.size())
  }

  // addItems marks dirty
  @Test
  fun `addItems marks dirty for each item added`() {
    inventory.clearDirty()

    val itemsToAdd = listOf(
      Inventory.Item(itemId = 1, amount = 5),
      Inventory.Item(itemId = 2, amount = 3)
    )
    inventory.addItems(itemsToAdd)

    assertTrue(inventory.isDirty())
    assertEquals(2, inventory.size())
  }

  // removeItem marks dirty
  @Test
  fun `removeItem marks dirty when item exists`() {
    inventory.addItem(Inventory.Item(itemId = 1, amount = 10))
    inventory.clearDirty()
    assertFalse(inventory.isDirty())

    val removed = inventory.removeItem(1)

    assertTrue(removed)
    assertTrue(inventory.isDirty())
    assertEquals(0, inventory.size())
  }

  @Test
  fun `removeItem does not mark dirty when item does not exist`() {
    inventory.clearDirty()

    val removed = inventory.removeItem(999)

    assertFalse(removed)
    assertFalse(inventory.isDirty())
  }

  // removeItemsIf marks dirty
  @Test
  fun `removeItemsIf marks dirty when items are removed`() {
    inventory.addItem(Inventory.Item(itemId = 1, amount = 10))
    inventory.addItem(Inventory.Item(itemId = 2, amount = 5))
    inventory.addItem(Inventory.Item(itemId = 3, amount = 15))
    inventory.clearDirty()
    assertFalse(inventory.isDirty())

    val removed = inventory.removeItemsIf { it.amount > 7 }

    assertTrue(removed)
    assertTrue(inventory.isDirty())
    assertEquals(1, inventory.size())
    assertTrue(inventory.hasItem(2))
  }

  @Test
  fun `removeItemsIf does not mark dirty when nothing is removed`() {
    inventory.addItem(Inventory.Item(itemId = 1, amount = 5))
    inventory.clearDirty()

    val removed = inventory.removeItemsIf { it.amount > 100 }

    assertFalse(removed)
    assertFalse(inventory.isDirty())
  }

  // clearItems marks dirty
  @Test
  fun `clearItems marks dirty when inventory is not empty`() {
    inventory.addItem(Inventory.Item(itemId = 1, amount = 10))
    inventory.clearDirty()
    assertFalse(inventory.isDirty())

    inventory.clearItems()

    assertTrue(inventory.isDirty())
    assertEquals(0, inventory.size())
  }

  @Test
  fun `clearItems does not mark dirty when inventory is already empty`() {
    inventory.clearDirty()

    inventory.clearItems()

    assertFalse(inventory.isDirty())
  }

  // updateItemAmount marks dirty
  @Test
  fun `updateItemAmount marks dirty when item exists`() {
    inventory.addItem(Inventory.Item(itemId = 1, amount = 10))
    inventory.clearDirty()
    assertFalse(inventory.isDirty())

    val updated = inventory.updateItemAmount(1, 20)

    assertTrue(updated)
    assertTrue(inventory.isDirty())
    assertEquals(20, inventory.getItem(1)?.amount)
  }

  @Test
  fun `updateItemAmount does not mark dirty when item does not exist`() {
    inventory.clearDirty()

    val updated = inventory.updateItemAmount(999, 10)

    assertFalse(updated)
    assertFalse(inventory.isDirty())
  }

  // removeAmount marks dirty
  @Test
  fun `removeAmount marks dirty when amount is decreased`() {
    inventory.addItem(Inventory.Item(itemId = 1, amount = 10))
    inventory.clearDirty()

    val removed = inventory.removeAmount(1, 3)

    assertTrue(removed)
    assertTrue(inventory.isDirty())
    assertEquals(7, inventory.getItem(1)?.amount)
  }

  @Test
  fun `removeAmount marks dirty when item is completely removed`() {
    inventory.addItem(Inventory.Item(itemId = 1, amount = 5))
    inventory.clearDirty()

    val removed = inventory.removeAmount(1, 5)

    assertTrue(removed)
    assertTrue(inventory.isDirty())
    assertEquals(0, inventory.size())
  }

  @Test
  fun `removeAmount returns false when amount is too large`() {
    inventory.addItem(Inventory.Item(itemId = 1, amount = 5))
    inventory.clearDirty()

    val removed = inventory.removeAmount(1, 10)

    assertFalse(removed)
    assertFalse(inventory.isDirty())
  }

  @Test
  fun `removeAmount returns false when item does not exist`() {
    inventory.clearDirty()

    val removed = inventory.removeAmount(999, 1)

    assertFalse(removed)
    assertFalse(inventory.isDirty())
  }

  @Test
  fun `removeAmount requires positive amount`() {
    inventory.addItem(Inventory.Item(itemId = 1, amount = 10))

    val exception = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
      inventory.removeAmount(1, -5)
    }
    assertTrue(exception.message?.contains("amount > 0") ?: false)
  }

  // decItem marks dirty
  @Test
  fun `decItem marks dirty when item exists`() {
    inventory.addItem(Inventory.Item(itemId = 1, amount = 10))
    inventory.clearDirty()

    val result = inventory.decItem(1)

    assertTrue(result)
    assertTrue(inventory.isDirty())
    assertEquals(9, inventory.getItem(1)?.amount)
  }

  @Test
  fun `decItem marks dirty when item amount reaches zero and is removed`() {
    inventory.addItem(Inventory.Item(itemId = 1, amount = 1))
    inventory.clearDirty()

    val result = inventory.decItem(1)

    assertTrue(result)
    assertTrue(inventory.isDirty())
    assertEquals(0, inventory.size())
  }

  @Test
  fun `decItem returns false when item does not exist`() {
    inventory.clearDirty()

    val result = inventory.decItem(999)

    assertFalse(result)
    assertFalse(inventory.isDirty())
  }

  // incItem marks dirty
  @Test
  fun `incItem marks dirty when item exists`() {
    inventory.addItem(Inventory.Item(itemId = 1, amount = 10))
    inventory.clearDirty()

    val result = inventory.incItem(1)

    assertTrue(result)
    assertTrue(inventory.isDirty())
    assertEquals(11, inventory.getItem(1)?.amount)
  }

  @Test
  fun `incItem returns false when item does not exist`() {
    inventory.clearDirty()

    val result = inventory.incItem(999)

    assertFalse(result)
    assertFalse(inventory.isDirty())
  }

  // Non-modifying operations should not affect dirty flag
  @Test
  fun `getItem does not affect dirty flag`() {
    inventory.addItem(Inventory.Item(itemId = 1, amount = 10))
    inventory.clearDirty()

    val item = inventory.getItem(1)

    assertFalse(inventory.isDirty())
    assertEquals(1, item?.itemId)
  }

  @Test
  fun `size does not affect dirty flag`() {
    inventory.addItem(Inventory.Item(itemId = 1, amount = 10))
    inventory.clearDirty()

    val size = inventory.size()

    assertFalse(inventory.isDirty())
    assertEquals(1, size)
  }

  @Test
  fun `isEmpty does not affect dirty flag`() {
    inventory.clearDirty()

    val isEmpty = inventory.isEmpty()

    assertFalse(inventory.isDirty())
    assertTrue(isEmpty)
  }

  @Test
  fun `hasItem does not affect dirty flag`() {
    inventory.addItem(Inventory.Item(itemId = 1, amount = 10))
    inventory.clearDirty()

    val hasItem = inventory.hasItem(1)

    assertFalse(inventory.isDirty())
    assertTrue(hasItem)
  }

  // clearDirty functionality
  @Test
  fun `clearDirty resets dirty flag`() {
    inventory.addItem(Inventory.Item(itemId = 1, amount = 10))
    assertTrue(inventory.isDirty())

    inventory.clearDirty()

    assertFalse(inventory.isDirty())
  }

  @Test
  fun `markDirty sets dirty flag`() {
    inventory.clearDirty()
    assertFalse(inventory.isDirty())

    inventory.markDirty()

    assertTrue(inventory.isDirty())
  }
}
