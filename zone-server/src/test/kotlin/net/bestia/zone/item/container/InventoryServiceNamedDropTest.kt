package net.bestia.zone.item.container

import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.item.findByIdentifierOrThrow
import net.bestia.zone.scenarios.ScenarioDataSetup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Taking one named copy out of several of the same template, against a real database - which is what this
 * needs and the pure [ItemContainerTest] cannot give it: copies are told apart by instance id, and an
 * instance only has one once it has been flushed.
 *
 * Charts are the case that made this matter. Two of them are two different maps, so "remove one chart" is
 * not a well-formed request and substituting the other one silently destroys surveyed land.
 *
 * Slot order is whatever JPA hands back and provably differs between two reads of the same container, so
 * nothing here may assume it. The refusals are what pin the behaviour down: an implementation that ignores
 * the named id has *something* to return and cannot help but return it.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class InventoryServiceNamedDropTest {

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var itemRepository: ItemRepository

  @Autowired
  private lateinit var testFixture: ScenarioDataSetup.TestFixture

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private val tx: TransactionTemplate get() = TransactionTemplate(transactionManager)

  private fun master() = testFixture.account1.masterIds.first()

  private fun chartId(): Long = tx.execute { itemRepository.findByIdentifierOrThrow("chart").id }!!

  /** The master's chart instance ids as a set, since slot order is not stable enough to compare. */
  private fun chartIds(masterId: Long, onlyFree: Boolean): Set<Long> = tx.execute {
    val itemId = chartId()
    masterRepository.findByIdOrThrow(masterId).container.slots
      .filter { !it.isStackable && it.template.id == itemId && (!onlyFree || it.isFree) }
      .map { it.uniqueId }
      .toSet()
  }!!

  private fun mintCharts(masterId: Long, count: Int) = tx.execute {
    val chart = itemRepository.findByIdentifierOrThrow("chart")
    repeat(count) { inventoryService.mintInstanceForMaster(masterId, chart) }
  }

  @Test
  fun `each named copy is the one that leaves`() {
    val master = master()
    mintCharts(master, 3)

    // Every held copy is dropped by name in a fixed order. One call could match a first-match
    // implementation by luck; all of them in a row could not.
    val toDrop = chartIds(master, onlyFree = true).sorted()
    assertTrue(toDrop.size >= 3, "need several tellable-apart charts to have a choice at all")

    toDrop.forEach { target ->
      val removed = inventoryService.removeOneFromMaster(master, chartId(), amount = 1, uniqueId = target)

      assertNotNull(removed, "chart $target was held and named, so it must come out")
      assertEquals(target, removed!!.uniqueId, "the named chart is the one that left")
      assertTrue(!chartIds(master, onlyFree = false).contains(target), "chart $target is gone")
    }

    assertTrue(chartIds(master, onlyFree = true).isEmpty(), "every named chart left, and only those")
  }

  @Test
  fun `a named copy that is not held is refused instead of substituted`() {
    val master = master()
    mintCharts(master, 2)
    val before = chartIds(master, onlyFree = false)

    assertNull(inventoryService.removeOneFromMaster(master, chartId(), amount = 1, uniqueId = 9_999_999L))
    assertEquals(before, chartIds(master, onlyFree = false), "a miss must not take a different copy")
  }

  @Test
  fun `a named copy promised to a trade is refused`() {
    val master = master()
    val tradeId = 900_101L
    mintCharts(master, 2)
    val promised = chartIds(master, onlyFree = true).first()

    assertNotNull(
      inventoryService.reserveForTrade(master, tradeId, itemId = chartId(), uniqueId = promised, amount = 1)
    )

    assertNull(inventoryService.removeOneFromMaster(master, chartId(), amount = 1, uniqueId = promised))
    assertTrue(chartIds(master, onlyFree = false).contains(promised), "an offered chart stays where it is")

    inventoryService.releaseAllTradeReservations(master, tradeId)
  }

  @Test
  fun `a zero uniqueId still takes any free copy`() {
    val master = master()
    mintCharts(master, 2)
    val before = chartIds(master, onlyFree = true)

    val removed = inventoryService.removeOneFromMaster(master, chartId(), amount = 1)

    assertNotNull(removed)
    assertTrue(before.contains(removed!!.uniqueId), "a chart never stacks, so a copy with an identity left")
    assertEquals(before - removed.uniqueId, chartIds(master, onlyFree = true), "exactly that one left")
  }
}
