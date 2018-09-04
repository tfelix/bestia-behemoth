package net.bestia.zoneserver.actor.chat

import mu.KotlinLogging
import net.bestia.messages.chat.ChatMessage
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import net.bestia.zoneserver.chat.ChatCommandService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * This actor processes chat messages from the clients to the bestia system. It
 * will check the preconditions and redirect it to the appropriate receivers
 * which will then in turn work with the chat message or redirect it to the chat
 * command system.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class ChatActor(
        private val chatCmdService: ChatCommandService
) : BaseClientMessageRouteActor() {

  private val publicChatActor = SpringExtension.actorOf(context, PublicChatActor::class.java)
  private val whisperChatActor = SpringExtension.actorOf(context, WhisperChatActor::class.java)
  private val guildChatActor = SpringExtension.actorOf(context, GuildChatActor::class.java)
  private val partyChatActor = SpringExtension.actorOf(context, PartyChatActor::class.java)

  init {
    requestMessages(ChatMessage::class.java, { msg: ChatMessage -> })
  }

  private fun onChatMessage(chatMsg: ChatMessage) {
    if (chatCmdService.isChatCommand(chatMsg.text)) {
      chatCmdService.executeChatCommand(chatMsg.accountId, chatMsg.text)
      return
    }

    when (chatMsg.chatMode) {
      ChatMessage.Mode.PUBLIC -> publicChatActor.tell(chatMsg, self)
      ChatMessage.Mode.WHISPER -> whisperChatActor.tell(chatMsg, self)
      ChatMessage.Mode.PARTY -> partyChatActor.tell(chatMsg, self)
      ChatMessage.Mode.GUILD -> guildChatActor.tell(chatMsg, self)

      else -> LOG.warn { "Message type not yet supported." }
    }
  }

  companion object {
    const val NAME = "chat"
  }
}