package net.bestia.messages.client

import java.io.Serializable

data class ClientDisconnectMessage(
        val accountId: Long
) : Serializable