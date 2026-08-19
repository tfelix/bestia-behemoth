package net.bestia.zone.item.container

import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.item.findByIdentifierOrThrow
import net.bestia.zone.item.instance.ItemInstanceRepository
import net.bestia.zone.scenarios.ScenarioDataSetup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * The durable half of a trade, against a real database - which is what these need and the pure
 * [ItemContainerTradeReservationTest] cannot give them: reservations are told apart by slot id, and a slot
 * only has one once it has been flushed.
 *
 * The point of the whole exercise is the last two tests: an exchange either happens in full or not at all,
 * and an item that changes hands arrives as the same item it left as.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class InventoryServiceTradeTest {

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var itemRepository: ItemRepository

  @Autowired
  private lateinit var itemInstanceRepository: ItemInstanceRepository

  @Autowired
  private lateinit var testFixture: ScenarioDataSetup.TestFixture

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private val tx: TransactionTemplate get() = TransactionTemplate(transactionManager)

  private fun masterA() = testFixture.account1.masterIds.first()
  private fun masterB() = testFixture.account2.masterIds.first()

  private fun heldAmount(masterId: Long, identifier: String): Int = tx.execute {
    val itemId = itemRepository.findByIdentifierOrThrow(identifier).id
    masterRepository.findByIdOrThrow(masterId).container.slots
      .filter { it.isFree && it.template.id == itemId }
      .sumOf { it.amount }
  }!!

  private fun heldInstanceIds(masterId: Long, identifier: String): List<Long> = tx.execute {
    val itemId = itemRepository.findByIdentifierOrThrow(identifier).id
    masterRepository.findByIdOrThrow(masterId).container.slots
      .filter { !it.isStackable && it.template.id == itemId }
      .map { it.uniqueId }
  }!!

  private fun appleId(): Long = tx.execute { itemRepository.findByIdentifierOrThrow("apple").id }!!

  @Test
  fun `a reserved stack is no longer counted as held and comes back on release`() {
    val tradeId = 900_001L
    val master = masterA()
    inventoryService.addItem(tx.execute { masterRepository.findByIdOrThrow(master) }!!, "apple", 10)

    val before = heldAmount(master, "apple")

    val reserved = inventoryService.reserveForTrade(master, tradeId, appleId(), uniqueId = 0L, amount = 4)

    assertNotNull(reserved)
    assertTrue(reserved!!.offerSlotId != 0L, "the offer line needs a real id to be retractable")
    assertEquals(before - 4, heldAmount(master, "apple"))

    val released = inventoryService.releaseTradeReservation(master, tradeId, reserved.offerSlotId)

    assertNotNull(released)
    assertEquals(4, released!!.amount)
    assertEquals(before, heldAmount(master, "apple"))
  }

  @Test
  fun `two offers of the same item stay separately retractable`() {
    val tradeId = 900_002L
    val master = masterA()
    inventoryService.addItem(tx.execute { masterRepository.findByIdOrThrow(master) }!!, "apple", 10)

    val first = inventoryService.reserveForTrade(master, tradeId, appleId(), 0L, 3)!!
    val second = inventoryService.reserveForTrade(master, tradeId, appleId(), 0L, 2)!!

    assertTrue(first.offerSlotId != second.offerSlotId, "each offer line is its own slot")

    inventoryService.releaseTradeReservation(master, tradeId, first.offerSlotId)

    val stillReserved = tx.execute {
      masterRepository.findByIdOrThrow(master).container.reservedSlots(tradeId)
    }!!
    assertEquals(1, stillReserved.size)
    assertEquals(2, stillReserved.single().amount)

    inventoryService.releaseAllTradeReservations(master, tradeId)
  }

  @Test
  fun `settlement moves both offers and preserves the identity of a unique item`() {
    val tradeId = 900_003L
    val a = masterA()
    val b = masterB()

    inventoryService.addItem(tx.execute { masterRepository.findByIdOrThrow(a) }!!, "apple", 6)

    // A forged sword with state worth losing: the whole reason instances move rather than being remade.
    val sword = inventoryService.mintInstanceForMaster(b, itemRepository.findByIdentifierOrThrow("iron_sword"))
    tx.execute {
      val instance = itemInstanceRepository.findById(sword.id).orElseThrow()
      instance.upgradeLevel = 7
      instance.durability = 33
      itemInstanceRepository.save(instance)
    }

    val applesBefore = heldAmount(a, "apple")

    val offeredApples = inventoryService.reserveForTrade(a, tradeId, appleId(), 0L, 4)!!
    val offeredSword = inventoryService.reserveForTrade(b, tradeId, sword.item.id, sword.id, 1)!!

    val settlement = inventoryService.settleTrade(
      tradeId = tradeId,
      masterAId = a,
      masterBId = b,
      expectedA = setOf(offeredApples.offerSlotId),
      expectedB = setOf(offeredSword.offerSlotId),
    )

    assertEquals(1, settlement.toMasterA.size)
    assertEquals(1, settlement.toMasterB.size)

    assertEquals(applesBefore - 4, heldAmount(a, "apple"), "the four offered apples left")
    assertEquals(4, heldAmount(b, "apple"), "and arrived")

    assertTrue(heldInstanceIds(b, "iron_sword").none { it == sword.id }, "the sword left its old owner")
    assertTrue(heldInstanceIds(a, "iron_sword").contains(sword.id), "as the same row, not a copy")

    val moved = tx.execute { itemInstanceRepository.findById(sword.id).orElseThrow() }!!
    assertEquals(7, moved.upgradeLevel, "a +7 sword is still a +7 sword after changing hands")
    assertEquals(33, moved.durability)

    assertTrue(
      tx.execute { masterRepository.findByIdOrThrow(a).container.reservedSlots(tradeId).isEmpty() }!!,
      "settlement clears the markers"
    )
  }

  @Test
  fun `settlement rolls back whole when a reserved slot is not what was expected`() {
    val tradeId = 900_004L
    val a = masterA()
    val b = masterB()

    inventoryService.addItem(tx.execute { masterRepository.findByIdOrThrow(a) }!!, "apple", 5)
    inventoryService.addItem(tx.execute { masterRepository.findByIdOrThrow(b) }!!, "apple", 5)

    val offeredByA = inventoryService.reserveForTrade(a, tradeId, appleId(), 0L, 2)!!
    val offeredByB = inventoryService.reserveForTrade(b, tradeId, appleId(), 0L, 3)!!

    val aBefore = heldAmount(a, "apple")
    val bBefore = heldAmount(b, "apple")

    // A slot id that was never part of this trade: the shape a stale session or a tampered client produces.
    assertThrows(TradeSettlementFailedException::class.java) {
      inventoryService.settleTrade(
        tradeId = tradeId,
        masterAId = a,
        masterBId = b,
        expectedA = setOf(offeredByA.offerSlotId, offeredByA.offerSlotId + 100_000L),
        expectedB = setOf(offeredByB.offerSlotId),
      )
    }

    assertEquals(aBefore, heldAmount(a, "apple"), "nothing moved")
    assertEquals(bBefore, heldAmount(b, "apple"))
    assertEquals(
      1,
      tx.execute { masterRepository.findByIdOrThrow(a).container.reservedSlots(tradeId).size }!!,
      "and the offers are still standing, ready to be given back"
    )

    inventoryService.releaseAllTradeReservations(a, tradeId)
    inventoryService.releaseAllTradeReservations(b, tradeId)
  }

  @Test
  fun `a promised item can no longer be dropped or spent on a craft`() {
    val tradeId = 900_005L
    val master = masterA()
    inventoryService.addItem(tx.execute { masterRepository.findByIdOrThrow(master) }!!, "apple", 3)

    val held = heldAmount(master, "apple")
    inventoryService.reserveForTrade(master, tradeId, appleId(), 0L, held)

    assertNull(
      inventoryService.removeOneFromMaster(master, appleId(), 1),
      "dropping an offered item would duplicate it"
    )
    assertTrue(
      !inventoryService.consumeAll(master, listOf(appleId() to 1)),
      "and so would feeding it to a craft"
    )

    inventoryService.releaseAllTradeReservations(master, tradeId)
    assertEquals(held, heldAmount(master, "apple"))
  }
}
