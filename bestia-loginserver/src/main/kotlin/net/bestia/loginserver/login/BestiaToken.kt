package net.bestia.loginserver.login

/**
 * Token which is issued to the client in order to perform a login.
 */
data class BestiaToken(
    val token: String
)