package net.bestia.zone.mocks

import net.bestia.zone.account.AccountConnectedEvent
import net.bestia.zone.account.AccountDisconnectedEvent
import net.bestia.zone.message.CMSG
import net.bestia.zone.message.SMSG
import net.bestia.zone.account.master.SelectMasterCMSG
import net.bestia.zone.message.processor.InMessageProcessor
import org.springframework.context.ApplicationEventPublisher
import java.lang.IllegalStateException
import kotlin.reflect.KClass

/**
 * A mock client which cuts out the message serialization and de-serialization and can be used to send and receive the
 * internal messages.
 */
class GameClientMock(
  val connectedPlayerId: Long,
  private val inMessageProcessor: InMessageProcessor,
  private val applicationEventPublisher: ApplicationEventPublisher,
  private val rxBuffer: MutableList<SMSG>
) {

  private var isConnected = false

  fun connect(selectMasterId: Long? = null) {
    if (!isConnected) {
      isConnected = true
      val accountConnectedEvent = AccountConnectedEvent(
        source = this,
        accountId = connectedPlayerId,
      )
      applicationEventPublisher.publishEvent(accountConnectedEvent)

      if (selectMasterId != null) {
        sendMessage(SelectMasterCMSG(connectedPlayerId, selectMasterId))
      }
    }
  }

  fun sendMessage(msg: CMSG) {
    inMessageProcessor.process(msg)
  }

  fun clearMessages() {
    rxBuffer.clear()
  }

  fun <T : SMSG> tryGetLastReceived(type: KClass<T>): T? {
    return rxBuffer.filterIsInstance(type.java).lastOrNull()
  }

  fun <T : SMSG> getLastReceived(type: KClass<T>): T {
    return tryGetLastReceived(type) ?: throw IllegalStateException("No message of type $type in buffer")
  }

  fun disconnect() {
    applicationEventPublisher.publishEvent(AccountDisconnectedEvent(this, connectedPlayerId))
    isConnected = false
  }
}