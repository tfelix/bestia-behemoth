package net.bestia.messages.client

import com.fasterxml.jackson.annotation.JsonProperty

import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId

/**
 * Simple ping message which can be send to the server. Will be answered with a
 * [PongMessage].
 *
 * @author Thomas Felix
 */
data class PingMessage(
    override val accountId: Long,

    @get:JsonProperty("s")
    val start: Long = System.currentTimeMillis(),
    val currentTimeMillis: Long = System.currentTimeMillis()
) : AccountMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "lat.req"
  }
}
