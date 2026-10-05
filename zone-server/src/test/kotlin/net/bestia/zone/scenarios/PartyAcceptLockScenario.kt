package net.bestia.zone.scenarios

import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import net.bestia.zone.party.Party
import net.bestia.zone.party.PartyService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Two players accepting into the last free seat each read the party as not full. Holding the party row for the
 * whole accept makes the second one count the first.
 */
class PartyAcceptLockScenario : BestiaNoSocketScenario() {

  @Autowired
  private lateinit var partyService: PartyService

  @Autowired
  private lateinit var entityManager: EntityManager

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private var partyId: Long? = null

  @Test
  fun `accepting an invitation waits while another transaction holds the party`() {
    val ownerId = clientPlayer1.connectedPlayerId
    val invitedId = clientPlayer2.connectedPlayerId
    val party = partyService.createParty(ownerId, "lockParty")
    partyId = party.id
    val invitation = partyService.invitePlayerToParty(ownerId, invitedId)
    val holderLocked = CountDownLatch(1)
    val releaseHolder = CountDownLatch(1)
    val threads = Executors.newFixedThreadPool(2)

    try {
      val holder = threads.submit {
        TransactionTemplate(transactionManager).execute {
          entityManager.find(Party::class.java, party.id, LockModeType.PESSIMISTIC_WRITE)
          holderLocked.countDown()
          releaseHolder.await(5, TimeUnit.SECONDS)
        }
      }
      assertTrue(holderLocked.await(5, TimeUnit.SECONDS))

      val accept = threads.submit { partyService.acceptInvitation(invitedId, invitation.invitationId) }

      // Kept well under H2's one-second lock timeout, so the waiting accept does not fail instead.
      Thread.sleep(300)
      assertFalse(accept.isDone, "the accept must wait for the party row instead of counting next to the holder")

      releaseHolder.countDown()
      holder.get(5, TimeUnit.SECONDS)
      accept.get(5, TimeUnit.SECONDS)
    } finally {
      releaseHolder.countDown()
      threads.shutdownNow()
    }
  }

  /** Other scenario classes share this Spring context and expect account 1 to start without a party. */
  @AfterAll
  fun disbandParty() {
    partyId?.let { partyService.disbandParty(clientPlayer1.connectedPlayerId, it) }
  }
}
