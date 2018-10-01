package net.bestia.messages.bestia

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.messages.AccountMessage

import net.bestia.messages.EntityMessage

/**
 * Client sends this message if it wants to switch to another active bestia.
 * This bestia from now on is responsible for gathering all visual information.
 * And the client will get updated about these data.
 *
 * @author Thomas Felix
 */
data class BestiaActivateMessage(
    override val accountId: Long,
    override val entityId: Long,
    @field:JsonProperty("pbid")
    val playerBestiaId: Long
) : EntityMessage, AccountMessage {

  val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "bestia.activate"
  }
}
