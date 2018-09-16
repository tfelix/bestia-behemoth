package net.bestia.messages

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Class can be send to a client and contains an entity id.
 *
 * @author Thomas Felix
 */
abstract class EntityJsonMessage(
        accId: Long,

        @field:JsonProperty("eid")
        val entityId: Long
) : JsonMessage(accId) {

  init {
    if (entityId < 0) {
      throw IllegalArgumentException("EntityID must be positive or 0.")
    }
  }

  override fun toString(): String {
    return "EntityJsonMessage[eeid: $entityId, accId: $accountId]"
  }

  companion object {
    private const val serialVersionUID = 1L
  }
}
