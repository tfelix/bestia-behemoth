package net.bestia.zone.mocks

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.AccountRepository
import net.bestia.zone.message.SMSG
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.socket.OutMessageHandler
import net.bestia.zone.util.AccountId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
@Profile("no-socket")
class GameClientMockFactory(
  private val inMessageProcessor: InMessageProcessor,
  private val applicationEventPublisher: ApplicationEventPublisher,
  private val accountRepository: AccountRepository,
  private val connectionAdapter: MockConnectionAdapter
) {

  /**
   * Hides the MessageSender interface from the public API as is also has the method "sendMessage"
   * and this is confusing when we want to send message from the client to the server.
   */
  @Component
  @Profile("no-socket")
  class MockConnectionAdapter : OutMessageHandler {

    /**
     * Concurrent throughout, because the real send path is: `ZoneEngine` hands each tick's component updates
     * to `AsyncJobExecutor`, so [sendMessage] runs on a pool thread while the test thread is reading the same
     * buffer. A plain list threw [java.util.ConcurrentModificationException] out of whichever scenario
     * happened to be reading when a tick flushed, which read as an unrelated flake.
     *
     * A snapshotting list rather than a synchronized one: readers iterate with `filterIsInstance` and have
     * nowhere to hold a lock.
     */
    val createdClientBuffer: MutableMap<AccountId, MutableList<SMSG>> = ConcurrentHashMap()

    override fun sendMessage(playerId: Long, outMessage: SMSG) {
      // add message to the according clients buffer.
      LOG.trace { "RX accountId: $playerId, msg: $outMessage" }

      createdClientBuffer[playerId]?.add(outMessage)
    }
  }

  @Transactional
  fun getGameClient(
    accountId: Long,
  ): GameClientMock {
    val account = accountRepository.findByIdOrNull(accountId)

    requireNotNull(account) { "Account $accountId was not found" }

    val buffer = connectionAdapter.createdClientBuffer.computeIfAbsent(accountId) {
      CopyOnWriteArrayList()
    }

    return GameClientMock(
      accountId,
      inMessageProcessor,
      applicationEventPublisher,
      buffer
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}