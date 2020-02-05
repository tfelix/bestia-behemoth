package net.bestia.messages.account

import java.io.Serializable

data class AccountLoginResponse(
    val username: String,
    val accountId: Long,
    val token: String
) : Serializable
