package net.bestia.messages.client

import net.bestia.messages.Envelope

data class ToClientEnvelope(
        val accountId: Long,
        override val content: Any
) : Envelope

// TODO Check if this is still needed
data class FromClientEnvelop(
        val accountId: Long,
        override val content: Any
) : Envelope