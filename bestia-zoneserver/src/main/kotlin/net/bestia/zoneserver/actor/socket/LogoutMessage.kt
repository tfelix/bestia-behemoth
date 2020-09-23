package net.bestia.zoneserver.actor.socket

/**
 * This message is send to the player in order to signal a (forced) logout from
 * the system.
 *
 * @author Thomas Felix
 */
data class LogoutMessage(
    val state: LoginResponse = LoginResponse.DENIED_NO_REASON
)