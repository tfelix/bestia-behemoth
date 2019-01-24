package net.bestia.zoneserver.actor.chat

import akka.actor.AbstractActor
import mu.KotlinLogging
import net.bestia.messages.chat.ChatMessage
import net.bestia.model.party.PartyRepository
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.actor.ActorComponentNoComponent
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendToClientActor

private val LOG = KotlinLogging.logger { }

/**
 * Handles party chat messages.
 *
 * @author Thomas Felix
 */
@ActorComponentNoComponent
class PartyChatActor(
    private val partyRepository: PartyRepository
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

    val party = partyRepository.findPartyByMembership(chatMsg.accountId)

    if (party == null) {
      // not a member of a party.
      LOG.debug { "Account ${chatMsg.accountId} is no member of any party." }
      val replyMsg = ChatMessage.getSystemMessage(chatMsg.accountId,
          "Not a member of a party.")
      sendToClient.tell(replyMsg, self)
      return
    }

    party.getMembers().forEach { member ->
      val reply = chatMsg.copy(accountId = member.id)
      sendToClient.tell(reply, self)
    }
  }

  companion object {
    const val NAME = "party"
  }
}