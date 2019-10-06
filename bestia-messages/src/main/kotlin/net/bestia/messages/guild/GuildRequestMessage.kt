package net.bestia.messages.guild

import com.fasterxml.jackson.annotation.JsonProperty

import net.bestia.messages.AccountMessage

/**
 * Asks the server to provide details about the requested guild.
 *
 * @author Thomas Felix
 */
data class GuildRequestMessage(
    override val accountId: Long,

    @JsonProperty("rgid")
    val requestedGuildId: Long
) : AccountMessage {
  companion object {
    const val MESSAGE_ID = "guild.req"
  }
}
