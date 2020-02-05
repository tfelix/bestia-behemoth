package net.bestia.messages.client

import net.bestia.messages.AccountMessage

/**
 * Answer to a [PingMessage] from the client.
 *
 * @author Thomas Felix
 */
class PongMessage(
    override val accountId: Long,
    val start: Long
) : AccountMessage
