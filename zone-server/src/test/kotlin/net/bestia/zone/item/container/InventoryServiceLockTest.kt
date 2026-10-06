package net.bestia.zone.item.container

import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.item.findByIdentifierOrThrow
import net.bestia.zone.scenarios.ScenarioDataSetup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Inventory writes come from Netty handlers, keyed and unkeyed async jobs and the trade expiry thread. Each one
 * loads the container, changes it and saves it, so two of them reading the same state lose one change unless
 * the master row is locked for the whole write.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class InventoryServiceLockTest {

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

  @Test
  fun `a grant waits while another transaction holds the master row`() {
    val masterId = testFixture.account2.masterIds.first()
    val apple = itemRepository.findByIdentifierOrThrow("apple")
    val holderLocked = CountDownLatch(1)
    val releaseHolder = CountDownLatch(1)
    val threads = Executors.newFixedThreadPool(2)

    try {
      val holder = threads.submit {
        TransactionTemplate(transactionManager).execute {
          masterRepository.findByIdForUpdate(masterId)
          holderLocked.countDown()
          releaseHolder.await(5, TimeUnit.SECONDS)
        }
      }
      assertTrue(holderLocked.await(5, TimeUnit.SECONDS))

      val grant = threads.submit { inventoryService.grantToMaster(masterId, apple.id, 1) }

      // Kept well under H2's one-second lock timeout, so the waiting grant does not fail instead.
      Thread.sleep(300)
      assertFalse(grant.isDone, "the grant must wait for the row lock instead of writing next to the holder")

      releaseHolder.countDown()
      holder.get(5, TimeUnit.SECONDS)
      grant.get(5, TimeUnit.SECONDS)

      // The fixture masters are shared with other test classes that count what they hold.
      inventoryService.consumeAll(masterId, listOf(apple.id to 1))
    } finally {
      releaseHolder.countDown()
      threads.shutdownNow()
    }
  }
}
