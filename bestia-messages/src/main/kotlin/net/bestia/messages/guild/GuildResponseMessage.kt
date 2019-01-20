package net.bestia.messages.guild

import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId
import net.bestia.model.guild.Guild

/**
 * Contains guild information.
 *
 * @author Thomas Felix
 */
data class GuildResponseMessage(
    override val accountId: Long,
    val guild: Guild
) : AccountMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "guild.info"
  }
}
