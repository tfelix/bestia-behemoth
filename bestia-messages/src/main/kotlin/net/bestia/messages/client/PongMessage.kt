package net.bestia.messages.client

import com.fasterxml.jackson.annotation.JsonProperty

import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId

/**
 * Answer to a [PingMessage] from the client.
 *
 * @author Thomas Felix
 */
class PongMessage(
    override val accountId: Long,

    @get:JsonProperty("s")
    val start: Long
) : AccountMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "lat.res"
  }
}
