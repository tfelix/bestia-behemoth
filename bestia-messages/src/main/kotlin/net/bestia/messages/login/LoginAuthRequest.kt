package net.bestia.messages.login

/**
 * Message is send if a webserver wants to authenticate a pending connection. It
 * will send the given access token from the request to the login server which
 * must respond accordingly.
 *
 * @author Thomas Felix
 */
data class LoginAuthRequest(
    /**
     * User provided login token which will be checked against in the database.
     *
     * @return Login token.
     */
    val token: String,
    val accountId: Long
)
