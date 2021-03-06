package net.bestia.messages.account

import java.io.Serializable

/**
 * Sends the user information about the status if his account registration.
 *
 * @author Thomas Felix
 */
data class AccountRegistrationResponse(val error: AccountRegistrationError) : Serializable
