package net.bestia.messages.account

import java.io.Serializable

/**
 * POJO used to request if a user name is available.
 *
 * @author Thomas Felix
 */
data class UserNameCheck(
    var username: String,
    var email: String,
    var isUsernameAvailable: Boolean? = null,
    var isEmailAvailable: Boolean? = null
) : Serializable
