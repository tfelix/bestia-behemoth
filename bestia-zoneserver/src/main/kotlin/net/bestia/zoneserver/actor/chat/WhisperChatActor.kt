package net.bestia.zoneserver.actor.chat

import akka.actor.AbstractActor
import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.chat.ChatMode
import net.bestia.messages.chat.ChatRequest
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
    @Qualifier(BQualifier.CLIENT_FORWARDER)
    private val sendClientActor: ActorRef
) : AbstractActor() {

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(ChatRequest::class.java, this::handleWhisper)
        .build()
  }

  /**
   * Handles an incoming whisper message.
   */
  private fun handleWhisper(chat: ChatRequest) {
    if (chat.chatMode != ChatMode.WHISPER) {
      LOG.warn { "Can not handle non whisper chat messages: $chat." }
      unhandled(chat)
      return
    }

    // Cant handle with no receiver name.
    val receiverNickname = chat.receiverNickname ?: return
    /*
    val acc = accService.getOnlineAccountByName(receiverNickname)

    if (acc == null) {
      LOG.debug { "Whisper receiver ${chatMsg.receiverNickname} not found." }
      return
    }*/

    val reply = chat.copy(10)
    sendClientActor.tell(reply, self)
  }

  companion object {
    const val NAME = "whisper"
  }
}
