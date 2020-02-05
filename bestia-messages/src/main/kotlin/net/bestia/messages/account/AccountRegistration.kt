package net.bestia.messages.account

/**
 * This POJO holds the data needed for an account registration.
 *
 * @author Thomas Felix
 */
data class AccountRegistration(
    val username: String,
    val email: String,
    val password: String,
    val gender: String,
    val campaignCode: String? = null
)