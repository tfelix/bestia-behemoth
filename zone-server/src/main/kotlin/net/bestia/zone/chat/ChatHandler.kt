package net.bestia.zone.chat

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.master.MasterNotFoundException
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.account.master.skill.BasicSkillGate
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class ChatHandler(
  private val outMessageProcessor: OutMessageProcessor,
  private val masterOperations: MasterResolver,
  private val connectionInfoService: ConnectionInfoService,
  private val world: WorldView,
  private val chatCommandHandler: ChatCommandHandler,
  private val basicSkillGate: BasicSkillGate
) : InMessageProcessor.IncomingMessageHandler<ChatCMSG> {
  override val handles = ChatCMSG::class

  override fun handle(msg: ChatCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    // Some sanity checks.
    if (msg.text.isEmpty()) {
      return true
    }

    // Talking to other players needs Basic Skill rank 2; commands deliberately do not, since a GM command
    // and a chat message only share a transport, and locking `/spawn` behind a novice skill would be absurd.
    if (msg.type != ChatCMSG.Type.COMMAND && !basicSkillGate.mayChat(msg.playerId)) {
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(OpError.BASIC_SKILL_CHAT_LOCKED))
      return true
    }

    when (msg.type) {
      ChatCMSG.Type.PUBLIC -> handlePublicChat(msg)
      ChatCMSG.Type.WHISPER -> handleWhisperChat(msg)
      ChatCMSG.Type.PARTY -> sendNotYetSupported(msg.playerId)
      ChatCMSG.Type.GUILD -> sendNotYetSupported(msg.playerId)
      ChatCMSG.Type.COMMAND -> handleChatCommand(msg)
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

    val position = world.modify(activeEntityId) { id ->
      get(id, Position::class)?.toVec3L()
    }

    if (position != null) {
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
      outMessageProcessor.sendToPlayer(msg.playerId, ChatSMSG.ERROR_UNKNOWN_USER)
    }
  }

  private fun handleChatCommand(msg: ChatCMSG) {
    chatCommandHandler.handleChatCommand(msg.playerId, msg.text)
  }

  private fun sendNotYetSupported(playerId: Long) {
    outMessageProcessor.sendToPlayer(playerId, ChatSMSG.ERROR_NOT_SUPPORTED)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
