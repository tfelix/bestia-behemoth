package net.bestia.messages.guild

import net.bestia.messages.AccountMessage

/**
 * Asks the server to provide details about the requested guild.
 *
 * @author Thomas Felix
 */
data class GuildRequest(
    override val accountId: Long,
    val requestedGuildId: Long
) : AccountMessage
