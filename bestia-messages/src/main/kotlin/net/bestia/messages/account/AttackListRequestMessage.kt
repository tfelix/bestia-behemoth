package net.bestia.messages.account

import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId

/**
 * Lists the current learned attacks of an bestia. The attacks are sorted in the
 * order of the minimum level in order to use them. The attacks of the currently
 * selected bestia are returned.
 *
 * @author Thomas Felix
 */
class AttackListRequestMessage(
    override val accountId: Long
) : AccountMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "attack.list.request"
  }
}
