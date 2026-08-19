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

/**
 * The rule an offered item has to obey: it stays where it is, and stops being reachable by everything else.
 *
 * Slot ids are all 0 here - they are only assigned on flush - so these exercise the id-free paths. Anything
 * that has to tell two reserved slots apart belongs in [InventoryServiceTradeTest], which has a database.
 */
class ItemContainerTradeReservationTest {

  private val apple = Item(id = 1L, identifier = "apple", weight = 2, type = Item.ItemType.ETC)
  private val sword = Item(
    id = 3L, identifier = "sword", weight = 10, type = Item.ItemType.EQUIP,
    equipSlot = EquipmentSlot.RIGHT_HAND
  )

  private val tradeId = 42L

  private lateinit var container: ItemContainer

  @BeforeEach
  fun setUp() {
    container = ItemContainer(ItemContainer.Type.MASTER)
  }

  @Test
  fun `reserving part of a stack splits it and leaves the rest spendable`() {
    container.addStackable(apple, 10)

    val reserved = container.reserveStackable(apple.id, 4, tradeId)

    assertNotNull(reserved)
    assertEquals(4, reserved!!.amount)
    assertEquals(2, container.slots.size)
    assertTrue(container.hasItem(apple.id, 6), "the six left over are still held")
    assertFalse(container.hasItem(apple.id, 7), "the four offered are not")
  }

  @Test
  fun `reserving the whole stack leaves nothing spendable`() {
    container.addStackable(apple, 10)

    assertNotNull(container.reserveStackable(apple.id, 10, tradeId))
    assertFalse(container.hasItem(apple.id, 1))
  }

  @Test
  fun `more than is held cannot be reserved`() {
    container.addStackable(apple, 3)

    assertNull(container.reserveStackable(apple.id, 4, tradeId))
    assertTrue(container.hasItem(apple.id, 3), "a refused reservation changes nothing")
  }

  @Test
  fun `a fresh grant never merges into a reserved pile`() {
    container.addStackable(apple, 10)
    container.reserveStackable(apple.id, 4, tradeId)

    container.addStackable(apple, 5)

    val free = container.slots.filter { it.isFree }.sumOf { it.amount }
    assertEquals(11, free, "the grant joined the free pile, not the offer")
    assertEquals(4, container.slots.single { it.isReserved }.amount)
  }

  @Test
  fun `a reserved stack cannot be spent`() {
    container.addStackable(apple, 10)
    container.reserveStackable(apple.id, 10, tradeId)

    assertFalse(container.removeStackable(apple.id, 1))
    assertNull(container.removeOne(apple.id, 1))
  }

  @Test
  fun `a reserved instance cannot be dropped or worn`() {
    container.addInstance(ItemInstance(item = sword))

    assertNotNull(container.reserveAnyInstance(sword.id, tradeId))

    assertNull(container.removeOne(sword.id, 1))
    assertFalse(container.equip(sword.id, 0L, EquipmentSlot.RIGHT_HAND))
    assertFalse(container.hasItem(sword.id))
  }

  @Test
  fun `a worn item cannot be offered`() {
    container.addInstance(ItemInstance(item = sword))
    assertTrue(container.equip(sword.id, 0L, EquipmentSlot.RIGHT_HAND))

    assertNull(container.reserveAnyInstance(sword.id, tradeId))
  }

  @Test
  fun `an already offered item cannot be offered twice`() {
    container.addInstance(ItemInstance(item = sword))
    container.reserveAnyInstance(sword.id, tradeId)

    assertNull(container.reserveAnyInstance(sword.id, 99L))
  }

  @Test
  fun `an unknown instance id reserves nothing`() {
    container.addInstance(ItemInstance(item = sword))

    // 0 means "no instance row yet", which reserveAnyInstance exists to handle - reserveInstance must not
    // treat it as a wildcard and grab whatever is lying around.
    assertNull(container.reserveInstance(0L, tradeId))
    assertNull(container.reserveInstance(12345L, tradeId))
  }

  @Test
  fun `reserveAnyInstance picks a free copy when the instance id is not known yet`() {
    container.addInstance(ItemInstance(item = sword))

    val reserved = container.reserveAnyInstance(sword.id, tradeId)

    assertNotNull(reserved)
    assertEquals(tradeId, reserved!!.reservedByTradeId)
  }

  @Test
  fun `releasing everything gives it back and folds the split stack together again`() {
    container.addStackable(apple, 10)
    container.addInstance(ItemInstance(item = sword))

    container.reserveStackable(apple.id, 4, tradeId)
    container.reserveAnyInstance(sword.id, tradeId)
    assertFalse(container.hasItem(sword.id), "both are promised away before the release")

    container.releaseAllReservations(tradeId)

    assertTrue(container.slots.none { it.isReserved })
    assertEquals(1, container.slots.count { it.template.id == apple.id }, "the two halves merged back")
    assertTrue(container.hasItem(apple.id, 10))
    assertTrue(container.hasItem(sword.id))
  }

  @Test
  fun `releasing one trade leaves another trade's reservations alone`() {
    container.addStackable(apple, 10)
    container.reserveStackable(apple.id, 4, tradeId)
    container.reserveStackable(apple.id, 3, 99L)

    container.releaseAllReservations(tradeId)

    assertEquals(1, container.reservedSlots(99L).size)
    assertTrue(container.hasItem(apple.id, 7))
    assertFalse(container.hasItem(apple.id, 8))
  }

  @Test
  fun `detaching a reserved slot hands out its instance and takes the slot away`() {
    val instance = ItemInstance(item = sword)
    container.addInstance(instance)
    val reserved = container.reserveAnyInstance(sword.id, tradeId)!!

    val detached = container.detachReserved(reserved.id, tradeId)

    assertNotNull(detached)
    assertSame(instance, detached!!.instance, "the instance row moves rather than being remade")
    assertEquals(sword.id, detached.template.id)
    assertTrue(container.slots.isEmpty())
  }

  @Test
  fun `detaching refuses a slot promised to a different trade`() {
    container.addInstance(ItemInstance(item = sword))
    val reserved = container.reserveAnyInstance(sword.id, tradeId)!!

    assertNull(container.detachReserved(reserved.id, 99L))
    assertEquals(1, container.slots.size)
  }
}
