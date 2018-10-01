package net.bestia.messages.entity

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.messages.AccountMessage

import net.bestia.messages.EntityMessage

/**
 * This message is send to the clients if a visible component for the clients
 * was deleted and needs to be removed.
 *
 * @author Thomas Felix
 */
data class EntityComponentDeleteMessage(
    override val accountId: Long,
    override val entityId: Long,
    @JsonProperty("cid")
    val componentId: Long
) : EntityMessage, AccountMessage {

  val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "entity.comp.del"
  }
}
