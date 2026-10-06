package net.bestia.zone.item.container

import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.scenarios.ScenarioDataSetup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals

/**
 * Grants several items to one master across separate [InventoryService.addItem] calls, each its own
 * transaction. Before [net.bestia.zone.item.persistence.ContainerSlot] had id-based equals/hashCode, and while `addItem` still took a
 * reusable `Master` instance, every slot added in an earlier call got merged into the `Set` again on
 * every later call's save(), duplicating earlier grants once per subsequent call.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class InventoryServiceRepeatedGrantTest {

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var testFixture: ScenarioDataSetup.TestFixture

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  @Test
  fun `granting three items across separate calls does not duplicate earlier grants`() {
    val masterId = testFixture.account1.masterIds.first()
    val transactionTemplate = TransactionTemplate(transactionManager)

    // Each call below is its own top-level transaction: this test method itself is not @Transactional.
    // Counted rather than assumed empty: the fixture master is shared with other tests in the same
    // database, and this test is about whether *these three* grants duplicate, not about what else is in the bag.
    val before = transactionTemplate.execute {
      masterRepository.findByIdOrThrow(masterId).container.slots
        .map { it.template.identifier to it.amount }
    }!!

    inventoryService.addItem(masterId, "apple", 12)
    inventoryService.addItem(masterId, "shoes", 1)
    inventoryService.addItem(masterId, "boots", 1)

    val slots = transactionTemplate.execute {
      masterRepository.findByIdOrThrow(masterId).container.slots
        .map { it.template.identifier to it.amount }
    }!!

    // Apples stack onto an apple stack that is already there; shoes and boots never stack.
    val newAppleSlots = if (before.any { it.first == "apple" }) 0 else 1
    assertEquals(before.size + newAppleSlots + 2, slots.size, "expected exactly one slot per granted item, got: $slots")
    assertEquals(before.amountOf("apple") + 12, slots.amountOf("apple"))
    assertEquals(before.count { it.first == "shoes" } + 1, slots.count { it.first == "shoes" })
    assertEquals(before.count { it.first == "boots" } + 1, slots.count { it.first == "boots" })
  }

  private fun List<Pair<String, Int>>.amountOf(identifier: String): Int {
    return filter { it.first == identifier }.sumOf { it.second }
  }
}
