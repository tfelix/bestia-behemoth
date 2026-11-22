package net.bestia.zone.stronghold

import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ItemHolder
import net.bestia.zone.item.persistence.GuildStorageRepository
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.item.persistence.findByIdentifierOrThrow
import net.bestia.zone.postal.persistence.ParcelRepository
import net.bestia.zone.scenarios.ScenarioDataSetup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A sealed guild storage sent away ends up whole in one parcel to the leader, against a real database. */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class GuildStorageMailTest {

  @Autowired
  private lateinit var mail: GuildStorageMail

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var guildStorages: GuildStorageRepository

  @Autowired
  private lateinit var parcels: ParcelRepository

  @Autowired
  private lateinit var items: ItemRepository

  @Autowired
  private lateinit var testFixture: ScenarioDataSetup.TestFixture

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private val tx: TransactionTemplate get() = TransactionTemplate(transactionManager)

  private fun leader() = testFixture.itemTestMasterIds[0]

  @AfterEach
  fun cleanUp() {
    tx.executeWithoutResult {
      parcels.findByRecipientMasterIdOrderByLatestArrival(leader()).filter { it.senderName == GUILD_NAME }.forEach(parcels::delete)
      guildStorages.deleteById(GUILD_ID)
    }
  }

  @Test
  fun `everything the storage holds goes into one parcel for the leader`() {
    val apple = tx.execute { items.findByIdentifierOrThrow("apple").id }!!
    inventoryService.addItem(leader(), "apple", 5)
    inventoryService.transferBetweenOwners(leader(), ItemHolder.Master, ItemHolder.GuildStorage(GUILD_ID), apple, 0L, 5)

    mail.post(GUILD_ID, leader(), GUILD_NAME)

    tx.executeWithoutResult {
      assertTrue(guildStorages.findById(GUILD_ID).orElseThrow().container.slots.isEmpty())
      val parcel = parcels.findByRecipientMasterIdOrderByLatestArrival(leader()).single { it.senderName == GUILD_NAME }
      assertEquals(5, parcel.container.slots.filter { it.template.id == apple }.sumOf { it.amount })
    }
  }

  private companion object {
    const val GUILD_ID = 9_002L
    const val GUILD_NAME = "Iron Wolves"
  }
}
