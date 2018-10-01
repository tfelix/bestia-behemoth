package net.bestia.messages.entity

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.messages.AccountMessage

import net.bestia.messages.EntityMessage

/**
 * By sending this message the client wants to get to know how he is able to
 * interact with the given entity. The server will respond with a list of
 * possible interactions.
 *
 * @author Thomas Felix
 */
class EntityInteractionRequestMessage(
    override val accountId: Long,
    override val entityId: Long,
    @JsonProperty("ieid")
    val interactedEntityId: Long
) : EntityMessage, AccountMessage {

  val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "entity.interactreq"
  }
}
