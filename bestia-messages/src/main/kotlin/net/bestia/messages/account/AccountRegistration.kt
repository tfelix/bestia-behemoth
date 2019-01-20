package net.bestia.messages.account

import net.bestia.model.account.Gender
import net.bestia.model.account.Hairstyle

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
    val hairstyle: Hairstyle,
    val campaignCode: String? = null
)