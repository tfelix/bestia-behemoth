package net.bestia.zone.chat.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.MasterNotFoundException
import net.bestia.zone.account.MasterResolver
import net.bestia.zone.skill.BasicSkillGate
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.guild.GuildAudience
import net.bestia.zone.party.PartyAudience
import org.springframework.stereotype.Component
import net.bestia.zone.chat.ChatCommandHandler
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.chat.ChatText
import net.bestia.zone.chat.ChatType
import net.bestia.zone.chat.GroupChat
import net.bestia.zone.identity.GroupMember

@Component
class ChatHandler(
  private val outMessageProcessor: OutMessageProcessor,
  private val masterOperations: MasterResolver,
  private val connectionInfoService: ConnectionInfoService,
  private val world: WorldView,
  private val chatCommandHandler: ChatCommandHandler,
  private val basicSkillGate: BasicSkillGate,
  private val partyAudience: PartyAudience,
  private val guildAudience: GuildAudience,
  private val groupChat: GroupChat,
) : IoMessageHandler<ChatCMSG> {
  override val wire = decoder(MessageCase.CHAT_CMSG) { accountId, envelope ->
    ChatCMSG.fromBnet(accountId, envelope.chatCmsg)
  }

  override fun handle(msg: ChatCMSG): Boolean {
    val text = ChatText.withoutControlCharacters(msg.text)

    if (ChatText.isTooLong(text)) {
      LOG.debug { "Dropping chat line of ${text.length} characters from player ${msg.playerId}" }
      return true
    }

    if (text.isEmpty()) {
      return true
    }

    val line = msg.copy(text = text)

    // Talking to other players needs Basic Skill rank 2; commands deliberately do not, since a GM command
    // and a chat message only share a transport, and locking `/spawn` behind a novice skill would be absurd.
    if (line.type != ChatType.COMMAND && !basicSkillGate.mayChat(line.playerId)) {
      outMessageProcessor.sendToPlayer(line.playerId, OperationErrorSMSG(OpError.BASIC_SKILL_CHAT_LOCKED))
      return true
    }

    when (line.type) {
      ChatType.PUBLIC -> handlePublicChat(line)
      ChatType.WHISPER -> handleWhisperChat(line)
      ChatType.PARTY -> handleGroupChat(line, partyAudience.of(line.playerId), OpError.PARTY_NOT_IN_PARTY)
      ChatType.GUILD -> handleGroupChat(line, guildAudience.of(line.playerId), OpError.GUILD_NOT_A_MEMBER)
      ChatType.COMMAND -> handleChatCommand(line)
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
        type = ChatType.WHISPER,
        senderUsername = masterOperations.getSelectedMasterByAccountId(msg.playerId).name
      )
    )
  }

  /** Party and guild chat, as [GroupChat] delivers it. */
  private fun handleGroupChat(msg: ChatCMSG, members: List<GroupMember>?, notInGroup: OpError) {
    if (members == null) {
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(notInGroup))
      return
    }

    val senderName = masterOperations.getSelectedMasterByAccountId(msg.playerId).name
    groupChat.deliver(msg.type, senderName, msg.text, members)
  }

  private fun handleChatCommand(msg: ChatCMSG) {
    chatCommandHandler.handleChatCommand(msg.playerId, msg.text)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
