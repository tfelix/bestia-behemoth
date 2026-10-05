package net.bestia.zone.mocks

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.persistence.AccountRepository
import net.bestia.zone.message.SMSG
import net.bestia.zone.message.AccountTaskExecutor
import net.bestia.zone.message.StateBatchSMSG
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.ConnectionTerminator
import net.bestia.zone.message.OutMessageHandler
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
  private val inbox: AccountTaskExecutor,
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
  class MockConnectionAdapter : OutMessageHandler, ConnectionTerminator {

    /**
     * Concurrent throughout, because the real send path is: `ZoneEngine` hands each tick's component updates
     * to its send executor and the IO lane's workers send on their own threads, so [sendMessage] runs off the
     * zone tick while the test thread, usually inside an Awaitility poll, is reading the same buffer. A plain
     * list threw [java.util.ConcurrentModificationException] out of the assertion rather than out of the code
     * under test, which read as an unrelated flake.
     *
     * A snapshotting list rather than a synchronized one: readers iterate with `filterIsInstance` and have
     * nowhere to hold a lock.
     */
    val createdClientBuffer: MutableMap<AccountId, MutableList<SMSG>> = ConcurrentHashMap()

    /** A mock client exists exactly when [getGameClient] gave it a buffer, which is what "connected" means here. */
    override val connectedAccountIds: Set<Long> get() = createdClientBuffer.keys.toSet()

    /** The reason of the last server-side disconnect per account, for tests of kicks. */
    val disconnectReasons: MutableMap<AccountId, String> = ConcurrentHashMap()

    override fun disconnect(accountId: Long, reason: String): Boolean {
      disconnectReasons[accountId] = reason

      return accountId in createdClientBuffer
    }

    override fun sendMessage(playerId: Long, outMessage: SMSG) {
      // A batch is unrolled, so a scenario asserts on the entity messages in it like on any other message.
      val received = if (outMessage is StateBatchSMSG) outMessage.messages else listOf(outMessage)
      LOG.trace { "RX accountId: $playerId, msg: $received" }

      // Encoded and dropped, so a message that cannot be put on the wire fails here as it would on a socket.
      outMessage.toBnetEnvelope()

      createdClientBuffer[playerId]?.addAll(received)
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
      inbox,
      applicationEventPublisher,
      buffer
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
