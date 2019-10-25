package net.bestia.messages.client

import net.bestia.messages.AccountMessage
import net.bestia.messages.Envelope

data class ClientEnvelope(
    override val accountId: Long,
    override val content: Any
) : Envelope, AccountMessage