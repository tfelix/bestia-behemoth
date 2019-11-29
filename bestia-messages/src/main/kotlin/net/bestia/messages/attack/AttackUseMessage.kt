package net.bestia.messages.attack

import com.fasterxml.jackson.annotation.JsonProperty

import net.bestia.messages.EntityMessage
import net.bestia.messages.MessageId

/**
 * A message from the client to the server to use an attack/skill.
 *
 * @author Thomas Felix
 */
data class AttackUseMessage(
    override val entityId: Long,

    @JsonProperty("aid")
    var attackId: Long,

    @JsonProperty("x")
    var x: Long,

    @JsonProperty("y")
    var y: Long,

    @JsonProperty("tid")
    var targetEntityId: Long = 0
) : EntityMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "attack.use"
  }
}
