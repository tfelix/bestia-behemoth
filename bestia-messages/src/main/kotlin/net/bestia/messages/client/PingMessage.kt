package net.bestia.messages.client

import net.bestia.messages.AccountMessage

/**
 * Simple ping message which can be send to the server. Will be answered with a
 * [PongMessage].
 *
 * @author Thomas Felix
 */
data class PingMessage(
    override val accountId: Long,
    val start: Long = System.currentTimeMillis(),
    val currentTimeMillis: Long = System.currentTimeMillis()
) : AccountMessage