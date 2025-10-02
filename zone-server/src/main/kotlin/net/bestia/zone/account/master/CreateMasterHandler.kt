package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.message.processor.OutMessageProcessor
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CreateMasterHandler(
  private val masterFactory: MasterFactory,
  private val outMessageProcessor: OutMessageProcessor
) : InMessageProcessor.IncomingMessageHandler<CreateMasterCMSG> {
  override val handles = CreateMasterCMSG::class

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
        body = msg.body
      )

      masterFactory.create(msg.playerId, masterCreateData)

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
