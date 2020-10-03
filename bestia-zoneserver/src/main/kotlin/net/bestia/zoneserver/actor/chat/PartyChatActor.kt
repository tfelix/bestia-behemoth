package net.bestia.zoneserver.actor.chat

import akka.actor.AbstractActor
import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.chat.ChatMode
import net.bestia.messages.chat.ChatRequest
import net.bestia.messages.chat.ChatResponse
import net.bestia.model.party.PartyRepository
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BQualifier
import org.springframework.beans.factory.annotation.Qualifier

private val LOG = KotlinLogging.logger { }

/**
 * Handles party chat messages.
 *
 * @author Thomas Felix
 */
@Actor
class PartyChatActor(
    private val partyRepository: PartyRepository,
    @Qualifier(BQualifier.CLIENT_FORWARDER)
    private val sendClientActor: ActorRef
) : AbstractActor() {

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
        .match(ChatRequest::class.java, this::handleParty)
        .build()
  }

  /**
   * Handles the party message. Finds all member in this party and then send
   * the message to them.
   */
  private fun handleParty(chat: ChatRequest) {
    // Sanity check.
    if (chat.chatMode != ChatMode.PARTY) {
      LOG.warn { "Can not handle non party chat messages: $chat" }
      unhandled(chat)
      return
    }

    val party = partyRepository.findPartyByMembership(chat.accountId)

    if (party == null) {
      // not a member of a party.
      LOG.debug { "Account ${chat.accountId} is no member of any party." }
      val replyMsg = ChatResponse.getSystemMessage(chat.accountId,
          "Not a member of a party.")
      sendClientActor.tell(replyMsg, self)
      return
    }

    party.getMembers().forEach { member ->
      val reply = chat.copy(accountId = member.id)
      sendClientActor.tell(reply, self)
    }
  }

  companion object {
    const val NAME = "party"
  }
}