package net.bestia.zoneserver.actor.chat

import mu.KotlinLogging
import net.bestia.messages.chat.ChatMode
import net.bestia.messages.chat.ChatRequest
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import net.bestia.zoneserver.chat.ChatCommandService

private val LOG = KotlinLogging.logger { }

/**
 * This actor processes chat messages from the clients to the bestia system. It
 * will check the preconditions and redirect it to the appropriate receivers
 * which will then in turn work with the chat message or redirect it to the chat
 * command system.
 *
 * @author Thomas Felix
 */
@Actor
class ChatActor(
    private val chatCmdService: ChatCommandService
) : DynamicMessageRoutingActor() {

  private val publicChatActor = SpringExtension.actorOf(context, PublicChatActor::class.java)
  private val whisperChatActor = SpringExtension.actorOf(context, WhisperChatActor::class.java)
  private val guildChatActor = SpringExtension.actorOf(context, GuildChatActor::class.java)
  private val partyChatActor = SpringExtension.actorOf(context, PartyChatActor::class.java)

  override fun createReceive(builder: BuilderFacade) {
    builder.matchRedirect(ChatRequest::class.java, this::onChatMessage)
  }

  private fun onChatMessage(chat: ChatRequest) {
    LOG.trace { "Received: $chat" }

    if (chatCmdService.isChatCommand(chat.text)) {
      chatCmdService.executeChatCommand(chat.accountId, chat.text)
      return
    }

    when (chat.chatMode) {
      ChatMode.PUBLIC -> publicChatActor.tell(chat, self)
      ChatMode.WHISPER -> whisperChatActor.tell(chat, self)
      ChatMode.PARTY -> partyChatActor.tell(chat, self)
      ChatMode.GUILD -> guildChatActor.tell(chat, self)
      else -> {
        LOG.warn { "Message type ${chat.chatMode} not yet supported." }
        unhandled(chat)
      }
    }
  }

  companion object {
    const val NAME = "chat"
  }
}