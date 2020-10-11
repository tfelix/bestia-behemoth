package net.bestia.messages.client

import net.bestia.messages.AccountMessage

data class ClientEnvelope(
    override val accountId: Long,
    val content: Any
) : AccountMessage