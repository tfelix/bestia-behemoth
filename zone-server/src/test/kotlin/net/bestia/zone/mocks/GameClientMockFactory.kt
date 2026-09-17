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
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import org.springframework.transaction.annotation.Transactional

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
     * Written by whichever thread the server sent from - the zone tick, or one of `AsyncJobExecutor`'s
     * workers - and read by the test thread, usually inside an Awaitility poll. So the buffers are
     * synchronized: an `ArrayList` here throws `ConcurrentModificationException` out of the assertion rather
     * than out of the code under test, which is a confusing way to learn nothing.
     */
    val createdClientBuffer: MutableMap<AccountId, MutableList<SMSG>> = ConcurrentHashMap()

    /** A mock client exists exactly when [getGameClient] gave it a buffer, which is what "connected" means here. */
    override val connectedAccountIds: Set<Long> get() = createdClientBuffer.keys.toSet()

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

    val buffer = connectionAdapter.createdClientBuffer.getOrPut(accountId) {
      Collections.synchronizedList(mutableListOf())
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