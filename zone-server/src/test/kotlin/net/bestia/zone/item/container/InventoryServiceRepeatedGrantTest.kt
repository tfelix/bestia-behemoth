package net.bestia.zone.item.container

import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.scenarios.ScenarioDataSetup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals

/**
 * Regression test for the exact pattern [net.bestia.zone.boot.DevDataBootstrapRunner] uses: granting
 * several items to the same in-memory [net.bestia.zone.account.master.Master] reference across
 * separate [InventoryService.addItem] calls (each its own transaction, so `master` is detached
 * between calls - unlike a single wrapping test transaction, which would never exercise the bug).
 * Before [ContainerSlot] had id-based equals/hashCode, every slot added in an earlier call got
 * merged into the `Set` again on every later call's save(), duplicating earlier grants once per
 * subsequent call.
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
  fun `granting three items across separate calls on the same master reference does not duplicate earlier grants`() {
    val masterId = testFixture.account1.masterIds.first()
    val transactionTemplate = TransactionTemplate(transactionManager)

    // DevDataBootstrapRunner works from a freshly-constructed, already-in-memory Master (its
    // `_slots` Set is never a lazy DB proxy). Force the same shape here by initializing the
    // collection once while a session is open, before detaching.
    val master = transactionTemplate.execute {
      masterRepository.findByIdOrThrow(masterId).also { it.container.slots.size }
    }!!

    // Each call below is its own top-level transaction (this test method itself is not
    // @Transactional), matching how DevDataBootstrapRunner invokes them - `master` becomes
    // detached after every commit, which is what makes the merge-duplication bug reproducible.
    inventoryService.addItem(master, "apple", 12)
    inventoryService.addItem(master, "shoes", 1)
    inventoryService.addItem(master, "boots", 1)

    val slots = transactionTemplate.execute {
      masterRepository.findByIdOrThrow(masterId).container.slots
        .map { it.template.identifier to it.amount }
    }!!

    assertEquals(3, slots.size, "expected exactly one slot per granted item, got: $slots")
    assertEquals(12, slots.first { it.first == "apple" }.second)
    assertEquals(1, slots.count { it.first == "shoes" })
    assertEquals(1, slots.count { it.first == "boots" })
  }
}
