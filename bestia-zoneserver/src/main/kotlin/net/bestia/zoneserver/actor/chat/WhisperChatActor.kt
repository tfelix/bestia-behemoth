package net.bestia.zoneserver.actor.chat

import akka.actor.AbstractActor
import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.chat.ChatMessage
import net.bestia.zoneserver.account.AccountService
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BQualifier
import org.springframework.beans.factory.annotation.Qualifier

private val LOG = KotlinLogging.logger { }

/**
 * Handles public chat of the user and sends them to all entities which can
 * receive them.
 *
 * @author Thomas Felix
 */
@Actor
class WhisperChatActor(
    private val accService: AccountService,
    @Qualifier(BQualifier.CLIENT_FORWARDER)
    private val sendClientActor: ActorRef
) : AbstractActor() {

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
        .match(ChatMessage::class.java, this::handleWhisper)
        .build()
  }

  /**
   * Handles an incoming whisper message.
   */
  private fun handleWhisper(chatMsg: ChatMessage) {
    if (chatMsg.chatMode != ChatMessage.Mode.WHISPER) {
      LOG.warn { "Can not handle non whisper chat messages: $chatMsg." }
      unhandled(chatMsg)
      return
    }

    // Cant handle with no receiver name.
    val receiverNickname = chatMsg.receiverNickname ?: return
    val acc = accService.getOnlineAccountByName(receiverNickname)

    if (acc == null) {
      LOG.debug { "Whisper receiver ${chatMsg.receiverNickname} not found." }
      return
    }

    val reply = chatMsg.copy(acc.id)
    sendClientActor.tell(reply, self)
  }

  companion object {
    const val NAME = "whisper"
  }
}
