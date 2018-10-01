package net.bestia.messages.account

import java.io.Serializable

/**
 * Sends a request to login a account and thus create a new login token.
 *
 * @author Thomas Felix
 */
data class AccountLoginRequest(
    val username: String,
    val password: String
) : Serializable
