package net.bestia.messages.client

import net.bestia.messages.Envelope

data class ClientEnvelope(
    val accountId: Long,
    override val content: Any
) : Envelope