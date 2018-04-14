package net.bestia.messages.client

import net.bestia.messages.Envelope

data class ToClientEnvelope(
        val accountId: Long,
        override val content: Any
) : Envelope

data class FromClientEnvelop(
        val accountId: Long,
        override val content: Any
) : Envelope