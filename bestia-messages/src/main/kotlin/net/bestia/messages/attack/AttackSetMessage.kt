package net.bestia.messages.attack

import com.fasterxml.jackson.annotation.JsonProperty

import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId

/**
 * Sets the attacks of the currently active bestia.
 *
 * @author Thomas Felix
 */
@Deprecated("Use components")
data class AttackSetMessage(
    override val accountId: Long,

    @JsonProperty("s1")
    val atkSlotId1: Int = 0,

    @JsonProperty("s2")
    val atkSlotId2: Int = 0,

    @JsonProperty("s3")
    val atkSlotId3: Int = 0,

    @JsonProperty("s4")
    val atkSlotId4: Int = 0,

    @JsonProperty("s5")
    val atkSlotId5: Int = 0
) : AccountMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "attack.set"
  }
}
