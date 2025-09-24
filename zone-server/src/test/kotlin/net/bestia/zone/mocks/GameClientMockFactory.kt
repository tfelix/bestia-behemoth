package net.bestia.zone.mocks

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.AccountRepository
import net.bestia.zone.message.SMSG
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.socket.OutMessageHandler
import net.bestia.zone.util.AccountId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
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

    val createdClientBuffer: MutableMap<AccountId, MutableList<SMSG>> = mutableMapOf()

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
      mutableListOf()
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