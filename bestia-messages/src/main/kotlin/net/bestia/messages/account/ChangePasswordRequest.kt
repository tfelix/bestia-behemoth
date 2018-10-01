package net.bestia.messages.account

import java.io.Serializable

/**
 * Password change request message which is send to the server. He will then
 * change the passwords of the user.
 *
 * @author Thomas Felix
 */
data class ChangePasswordRequest(
    val accountName: String,
    val oldPassword: String,
    val newPassword: String
) : Serializable
