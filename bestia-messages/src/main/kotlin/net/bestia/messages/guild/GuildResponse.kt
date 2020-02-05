package net.bestia.messages.guild

import net.bestia.messages.AccountMessage

/**
 * Contains guild information.
 *
 * @author Thomas Felix
 */
data class GuildResponse(
    override val accountId: Long
    // val guild: Guild
) : AccountMessage
