package net.bestia.messages.account

import net.bestia.model.account.Gender

/**
 * This POJO holds the data needed for an account registration.
 *
 * @author Thomas Felix
 */
data class AccountRegistration(
    val username: String,
    val email: String,
    val password: String,
    val gender: Gender,
    val campaignCode: String? = null
)