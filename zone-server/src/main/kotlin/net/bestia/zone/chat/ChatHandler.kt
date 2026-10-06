package net.bestia.zone.chat

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.master.MasterNotFoundException
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.account.master.skill.BasicSkillGate
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class ChatHandler(
  private val outMessageProcessor: OutMessageProcessor,
  private val masterOperations: MasterResolver,
  private val connectionInfoService: ConnectionInfoService,
  private val world: WorldView,
  private val chatCommandHandler: ChatCommandHandler,
  private val basicSkillGate: BasicSkillGate
) : IoMessageHandler<ChatCMSG> {
  override val wire = decoder(MessageCase.CHAT_CMSG) { accountId, envelope ->
    ChatCMSG.fromBnet(accountId, envelope.chatCmsg)
  }

  override fun handle(msg: ChatCMSG): Boolean {
    val text = withoutControlCharacters(msg.text)

    // The client caps its input at the same length, so a longer line is a forged one.
    if (text.codePointCount(0, text.length) > MAX_TEXT_LENGTH) {
      LOG.debug { "Dropping chat line of ${text.length} characters from player ${msg.playerId}" }
      return true
    }

    if (text.isEmpty()) {
      return true
    }

    val line = msg.copy(text = text)

    // Talking to other players needs Basic Skill rank 2; commands deliberately do not, since a GM command
    // and a chat message only share a transport, and locking `/spawn` behind a novice skill would be absurd.
    if (line.type != ChatCMSG.Type.COMMAND && !basicSkillGate.mayChat(line.playerId)) {
      outMessageProcessor.sendToPlayer(line.playerId, OperationErrorSMSG(OpError.BASIC_SKILL_CHAT_LOCKED))
      return true
    }

    when (line.type) {
      ChatCMSG.Type.PUBLIC -> handlePublicChat(line)
      ChatCMSG.Type.WHISPER -> handleWhisperChat(line)
      ChatCMSG.Type.PARTY -> sendNotYetSupported(line.playerId)
      ChatCMSG.Type.GUILD -> sendNotYetSupported(line.playerId)
      ChatCMSG.Type.COMMAND -> handleChatCommand(line)
      else -> {
        LOG.warn { "Received unsupported chat type: ${line.type} from player ${line.playerId}" }
      }
    }

    return true
  }

  private fun handlePublicChat(msg: ChatCMSG) {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    val chatSMSG = ChatSMSG(
      text = msg.text,
      type = msg.type,
      senderUsername = masterOperations.getSelectedMasterByAccountId(msg.playerId).name,
      senderEntityId = activeEntityId
    )

    // Posted: who sees the entity is tick-thread state, and this handler runs on the IO lane.
    world.post { outMessageProcessor.sendToObserversOf(this, activeEntityId, chatSMSG) }
  }

  /**
   * A whisper is a one-off: unlike component state, nothing will send it again, so the sender has to be told
   * when it went nowhere. Both ways it can go nowhere give the same answer - see
   * [OpError.CHAT_WHISPER_TARGET_UNAVAILABLE].
   */
  private fun handleWhisperChat(msg: ChatCMSG) {
    val targetUsername = requireNotNull(msg.targetUsername)

    val targetAccountId = try {
      masterOperations.getAccountIdByMasterName(targetUsername)
    } catch (e: MasterNotFoundException) {
      null
    }

    if (targetAccountId == null || !outMessageProcessor.isPlayerConnected(targetAccountId)) {
      outMessageProcessor.sendToPlayer(
        msg.playerId,
        OperationErrorSMSG(OpError.CHAT_WHISPER_TARGET_UNAVAILABLE, listOf(targetUsername))
      )

      return
    }

    outMessageProcessor.sendToPlayer(
      targetAccountId,
      ChatSMSG(
        text = msg.text,
        type = ChatCMSG.Type.WHISPER,
        senderUsername = masterOperations.getSelectedMasterByAccountId(msg.playerId).name
      )
    )
  }

  private fun handleChatCommand(msg: ChatCMSG) {
    chatCommandHandler.handleChatCommand(msg.playerId, msg.text)
  }

  private fun sendNotYetSupported(playerId: Long) {
    outMessageProcessor.sendToPlayer(playerId, ChatSMSG.ERROR_NOT_SUPPORTED)
  }

  /** Control and direction-override characters only ever garble, or disguise, someone else's chat window. */
  private fun withoutControlCharacters(text: String): String {
    return text.filterNot { Character.isISOControl(it) || it in INVISIBLE_FORMATTING }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** Must match `max_length` of the ChatInput in the client's Chat.tscn. */
    const val MAX_TEXT_LENGTH = 200

    private val INVISIBLE_FORMATTING =
      ('\u200B'..'\u200F') + ('\u202A'..'\u202E') + ('\u2066'..'\u2069') + '\uFEFF'
  }
}
