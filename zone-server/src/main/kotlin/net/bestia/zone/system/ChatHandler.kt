package net.bestia.zone.system

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.MasterNotFoundException
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.ecs2.ZoneServer
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.message.processor.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class ChatHandler(
  private val outMessageProcessor: OutMessageProcessor,
  private val masterOperations: MasterResolver,
  private val connectionInfoService: ConnectionInfoService,
  private val zoneServer: ZoneServer
) : InMessageProcessor.IncomingMessageHandler<ChatCMSG> {
  override val handles = ChatCMSG::class

  override fun handle(msg: ChatCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    // Some sanity checks.
    if (msg.text.isEmpty()) {
      return true
    }

    when (msg.type) {
      ChatCMSG.Type.PUBLIC -> handlePublicChat(msg)
      ChatCMSG.Type.WHISPER -> handleWhisperChat(msg)
      ChatCMSG.Type.PARTY -> sendNotYetSupported(msg.playerId)
      ChatCMSG.Type.GUILD -> sendNotYetSupported(msg.playerId)
      ChatCMSG.Type.COMMAND -> sendNotYetSupported(msg.playerId)
      else -> {
        LOG.warn { "Received unsupported chat type: ${msg.type} from player ${msg.playerId}" }
      }
    }

    return true
  }

  private fun handlePublicChat(msg: ChatCMSG) {
    // TODO if this section here is needed more often (position of active entity) check if it make sense
    //   to encapsulate this.
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    val chatSMSG = ChatSMSG(
      text = msg.text,
      type = msg.type,
      senderUsername = masterOperations.getSelectedMasterByAccountId(msg.playerId).name,
      senderEntityId = activeEntityId
    )

    val position = zoneServer.withEntityReadLock(activeEntityId) { entity ->
      entity.get(Position::class)?.toVec3L()
    }

    if(position != null) {
      outMessageProcessor.sendToAllPlayersInRange(position, chatSMSG)
    }
  }

  private fun handleWhisperChat(msg: ChatCMSG) {
    requireNotNull(msg.targetUsername)

    val chatSMSG = ChatSMSG(
      text = msg.text,
      type = ChatCMSG.Type.WHISPER,
      senderUsername = masterOperations.getSelectedMasterByAccountId(msg.playerId).name
    )

    try {
      val targetAccountId = masterOperations.getAccountIdByMasterName(msg.targetUsername)

      outMessageProcessor.sendToPlayer(targetAccountId, chatSMSG)
    } catch (e: MasterNotFoundException) {
      outMessageProcessor.sendToPlayer(msg.playerId, ChatSMSG.Companion.ERROR_UNKNOWN_USER)
    }
  }

  private fun sendNotYetSupported(playerId: Long) {
    outMessageProcessor.sendToPlayer(playerId, ChatSMSG.Companion.ERROR_NOT_SUPPORTED)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}