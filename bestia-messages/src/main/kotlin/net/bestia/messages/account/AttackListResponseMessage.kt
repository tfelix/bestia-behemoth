package net.bestia.messages.account

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId
import net.bestia.model.domain.BestiaAttack

/**
 * Lists the current learned attacks of an bestia. The attacks are sorted in the
 * order of the minimum level in order to use them. The attacks of the currently
 * selected bestia are returned.
 *
 * @author Thomas Felix
 */
data class AttackListResponseMessage(
    override val accountId: Long,
    @JsonProperty("atks")
    val attacks: MutableList<BestiaAttack>? = null
) : AccountMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "attack.list.response"
  }
}
