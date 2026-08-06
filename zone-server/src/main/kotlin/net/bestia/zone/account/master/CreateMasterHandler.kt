package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CreateMasterHandler(
  private val masterCreateOperation: MasterCreateOperation,
  private val outMessageProcessor: OutMessageProcessor
) : InMessageProcessor.IncomingMessageHandler<CreateMasterCMSG> {
  override val handles = CreateMasterCMSG::class

  /**
   * Safe to report a rejection from inside this transaction only because [MasterCreateOperation] runs the
   * insert in one of its own — see there for what goes wrong when the failing transaction is this one.
   */
  @Transactional
  override fun handle(msg: CreateMasterCMSG): Boolean {
    LOG.trace { "RX: $msg" }
    try {
      // Create MasterCreateData from the message
      val masterCreateData = MasterFactory.CreateMasterData(
        name = msg.name,
        hairColor = msg.hairColor,
        skinColor = msg.skinColor,
        hair = msg.hair,
        face = msg.face,
        body = msg.body,
        spawnPointId = msg.spawnPointId
      )

      masterCreateOperation.create(msg.playerId, masterCreateData)

      // Only acknowledge the creation. The client re-fetches the master list via GetMaster
      // when it navigates back to the selection screen, so pushing it here would be redundant.
      outMessageProcessor.sendToPlayer(msg.playerId, MasterCreatedSMSG)
    } catch (e: MasterCreateException) {
      outMessageProcessor.sendToPlayer(msg.playerId, MasterErrorSMSG(e.errorCode))
    } catch (_: Exception) {
      outMessageProcessor.sendToPlayer(msg.playerId, MasterErrorSMSG(MasterErrorSMSG.MasterErrorCode.GENERAL_ERROR))
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
