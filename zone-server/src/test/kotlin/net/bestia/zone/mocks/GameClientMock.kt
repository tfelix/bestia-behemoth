package net.bestia.zone.mocks

import net.bestia.account.Authority
import net.bestia.zone.session.AccountConnectedEvent
import net.bestia.zone.session.AccountDisconnectedEvent
import net.bestia.zone.message.CMSG
import net.bestia.zone.message.SMSG
import net.bestia.zone.master.net.SelectMasterCMSG
import net.bestia.zone.message.AccountTaskExecutor
import net.bestia.zone.message.InMessageProcessor
import org.springframework.context.ApplicationEventPublisher
import java.lang.IllegalStateException
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * A mock client which cuts out the message serialization and de-serialization and can be used to send and receive the
 * internal messages.
 */
class GameClientMock(
  val connectedPlayerId: Long,
  private val inMessageProcessor: InMessageProcessor,
  private val inbox: AccountTaskExecutor,
  private val applicationEventPublisher: ApplicationEventPublisher,
  private val rxBuffer: MutableList<SMSG>,
  // Authorities granted to the mocked client. Defaults to all so authority-gated commands work.
  private val authorities: Set<Authority> = Authority.entries.toSet()
) {

  private var isConnected = false

  fun connect(selectMasterId: Long? = null) {
    if (!isConnected) {
      isConnected = true
      val accountConnectedEvent = AccountConnectedEvent(
        source = this,
        accountId = connectedPlayerId,
        authorities = authorities,
      )
      publishInOrder(accountConnectedEvent)

      if (selectMasterId != null) {
        sendMessage(SelectMasterCMSG(connectedPlayerId, selectMasterId))
      }
    }
  }

  /** Through the account's inbox like a real connection, and back once the handler has run. */
  fun sendMessage(msg: CMSG) {
    inMessageProcessor.submit(msg).get(5, TimeUnit.SECONDS)
  }

  /** What [net.bestia.zone.socket.ClientMessageHandler] does with a connection event. */
  private fun publishInOrder(event: Any) {
    inbox.onIo(connectedPlayerId) { applicationEventPublisher.publishEvent(event) }
      .get(5, TimeUnit.SECONDS)
  }

  fun clearMessages() {
    synchronized(rxBuffer) { rxBuffer.clear() }
  }

  /**
   * A copy taken under the buffer's own lock.
   *
   * The buffer is a `Collections.synchronizedList`, which guards each operation but *not* iteration - and the
   * server writes to it from the zone tick and from the IO lane while the test thread reads.
   */
  private fun received(): List<SMSG> = synchronized(rxBuffer) { rxBuffer.toList() }

  fun <T : SMSG> tryGetLastReceived(type: KClass<T>): T? {
    return received().filterIsInstance(type.java).lastOrNull()
  }

  /** Unlike [tryGetLastReceived], checks the whole buffer rather than only the most recent message
   * of [type] - useful when unrelated background traffic (e.g. periodic regen) of the same type
   * may arrive after the message under test. */
  fun <T : SMSG> receivedAny(type: KClass<T>, predicate: (T) -> Boolean): Boolean {
    return received().filterIsInstance(type.java).any(predicate)
  }

  fun <T : SMSG> getLastReceived(type: KClass<T>): T {
    return tryGetLastReceived(type) ?: throw IllegalStateException("No message of type $type in buffer")
  }

  fun disconnect() {
    if (isConnected) {
      publishInOrder(AccountDisconnectedEvent(this, connectedPlayerId))
      isConnected = false
    }
  }
}