package net.bestia.messages.entity

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonRawValue
import net.bestia.messages.AccountMessage
import net.bestia.messages.EntityMessage

/**
 * This message is send if a component has changed and the clients data model
 * should be updated to reflect this change. The component data is added inside
 * the payload field.
 */
data class EntityComponentSyncMessage(
    override val accountId: Long,
    override val entityId: Long,

    @JsonProperty("ct")
    val componentName: String,

    @JsonRawValue
    val payload: String,

    @JsonProperty("l")
    val latency: Int
) : EntityMessage, AccountMessage {

  init {
    if (latency < 0) {
      throw IllegalArgumentException("Latency can not be negative.")
    }
  }

  val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "entity.comp"
  }
}
