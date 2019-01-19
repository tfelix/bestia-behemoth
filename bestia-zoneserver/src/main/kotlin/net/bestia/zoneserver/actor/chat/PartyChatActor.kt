package net.bestia.zoneserver.actor.chat

import akka.actor.AbstractActor
import mu.KotlinLogging
import net.bestia.messages.chat.ChatMessage
import net.bestia.model.dao.PartyDAO
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendToClientActor
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * Handles party chat messages.
 *
 * @author Thomas Felix
 */
@ActorComponent
class PartyChatActor(
    private val partyDao: PartyDAO
) : AbstractActor() {

  private val sendToClient = SpringExtension.actorOf(context, SendToClientActor::class.java)

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
        .match(ChatMessage::class.java, this::handleParty)
        .build()
  }

  /**
   * Handles the party message. Finds all member in this party and then send
   * the message to them.
   */
  private fun handleParty(chatMsg: ChatMessage) {
    // Sanity check.
    if (chatMsg.chatMode != ChatMessage.Mode.PARTY) {
      LOG.warn { "Can not handle non party chat messages: $chatMsg" }
      unhandled(chatMsg)
      return
    }

    val party = partyDao.findPartyByMembership(chatMsg.accountId)

    if (party == null) {
      // not a member of a party.
      LOG.debug { "Account ${chatMsg.accountId} is no member of any party." }
      val replyMsg = ChatMessage.getSystemMessage(chatMsg.accountId,
          "Not a member of a party.")
      sendToClient.tell(replyMsg, self)
      return
    }

    party.members.forEach { member ->
      val reply = chatMsg.copy(accountId = member.id)
      sendToClient.tell(reply, self)
    }
  }

  companion object {
    const val NAME = "party"
  }
}