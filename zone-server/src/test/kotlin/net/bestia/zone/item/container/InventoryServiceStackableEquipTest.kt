package net.bestia.zone.item.container

import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.item.PlayerItemIds
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.persistence.ItemInstanceRepository
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.item.persistence.findByIdentifierOrThrow
import net.bestia.zone.scenarios.ScenarioDataSetup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Torches against a real database: the split and the fold-back each add and delete an instance row, and only a
 * flush shows whether the rows go in an order the foreign keys accept.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class InventoryServiceStackableEquipTest {

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

  private val master get() = testFixture.account1.masterIds.first()

  private val torchId: Long get() = tx.execute { itemRepository.findByIdentifierOrThrow("torch").id }!!

  /** The fixture master is shared with other tests, so nothing of this one may stay behind. */
  @AfterEach
  fun dropTorches() {
    inventoryService.unequip(master, null, EquipmentSlot.LIGHT_SOURCE)
    tx.execute {
      val container = masterRepository.findByIdOrThrow(master).container
      container.slots.filter { it.template.id == torchId }
        .forEach { container.removeNamed(torchId, it.amount, it.uniqueId, it.provenance) }
    }
  }

  @Test
  fun `a torch worn from the pile is its own copy, and goes back when it was never lit`() {
    inventoryService.addItem(master, "torch", 5)
    val copyId = PlayerItemIds.next()

    assertTrue(inventoryService.equipFromPile(master, null, torchId, null, copyId, EquipmentSlot.LIGHT_SOURCE))
    assertEquals(4, pile())
    assertEquals(copyId, wornCopy())

    assertTrue(inventoryService.unequip(master, null, EquipmentSlot.LIGHT_SOURCE))
    assertEquals(5, pile())
    assertFalse(itemInstanceRepository.existsById(copyId))
  }

  @Test
  fun `a torch that burnt for a while stays a copy of its own`() {
    inventoryService.addItem(master, "torch", 2)
    val copyId = PlayerItemIds.next()
    inventoryService.equipFromPile(master, null, torchId, null, copyId, EquipmentSlot.LIGHT_SOURCE)
    tx.execute { itemInstanceRepository.findById(copyId).get().wear(10) }

    inventoryService.unequip(master, null, EquipmentSlot.LIGHT_SOURCE)

    assertEquals(1, pile())
    assertTrue(itemInstanceRepository.existsById(copyId))
  }

  private fun pile(): Int = tx.execute {
    masterRepository.findByIdOrThrow(master).container.pileAmount(torchId, null)
  }!!

  private fun wornCopy(): Long? = tx.execute {
    masterRepository.findByIdOrThrow(master).container.equipped()[EquipmentSlot.LIGHT_SOURCE]?.uniqueId
  }
}
