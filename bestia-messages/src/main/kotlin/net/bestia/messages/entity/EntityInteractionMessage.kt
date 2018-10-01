package net.bestia.messages.entity

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.messages.AccountMessage
import net.bestia.messages.EntityMessage
import net.bestia.model.entity.InteractionType

/**
 * By sending this message to the client the client is informed how
 * he will be able to interact with this entity.
 *
 * @author Thomas Felix
 */
data class EntityInteractionMessage(
    override val accountId: Long,
    override val entityId: Long,
    @JsonProperty("is")
    private val interactions: Set<InteractionType>
) : AccountMessage, EntityMessage {

  val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "entity.interact"
  }
}
