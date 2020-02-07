package net.bestia.zoneserver.actor.socket

import akka.actor.AbstractActor
import net.bestia.messages.AccountMessage
import net.bestia.zoneserver.account.AuthenticationService
import net.bestia.zoneserver.account.LoginServiceImpl
import net.bestia.zoneserver.account.PlayerEntitySetupService
import net.bestia.zoneserver.actor.Actor

/**
 * This message is send to the player in order to signal a (forced) logout from
 * the system.
 *
 * @author Thomas Felix
 */
data class LogoutMessage(
    val state: LoginResponse = LoginResponse.NO_REASON,
    val reason: String = ""
)

/**
 * Message is send if a webserver wants to authenticate a pending connection. It
 * will send the given access token from the request to the login server which
 * must respond accordingly.
 *
 * @author Thomas Felix
 */
data class AuthRequest(
    /**
     * User provided login token which will be checked against in the database.
     *
     * @return Login token.
     */
    val token: String,
    val accountId: Long
)

data class AuthResponse(
    override val accountId: Long,
    val response: LoginResponse
) : AccountMessage

enum class LoginResponse {
  SUCCESS,

  /**
   * Login creds are not valid.
   */
  UNAUTHORIZED,

  /**
   * Currently no logins are allowed. Client should not retry.
   */
  NO_LOGINS_ALLOWED,

  NO_REASON
}

/**
 * Tries to authenticate a client.
 */
@Actor
class AuthenticationCheckActor(
    private val authenticationService: AuthenticationService,
    private val loginService: LoginServiceImpl,
    private val setupService: PlayerEntitySetupService
) : AbstractActor() {
  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(AuthRequest::class.java, this::authenticate)
        .build()
  }

  private fun authenticate(msg: AuthRequest) {
    val isAuthenticated = authenticationService.isUserAuthenticated(msg.accountId, msg.token)
    if (!isAuthenticated) {
      sender.tell(AuthResponse(accountId = msg.accountId, response = LoginResponse.UNAUTHORIZED), self)
      return
    }

    val isLoginAllowed = loginService.isLoginAllowedForAccount(msg.accountId)
    if (!isLoginAllowed) {
      sender.tell(AuthResponse(accountId = msg.accountId, response = LoginResponse.NO_LOGINS_ALLOWED), self)
      return
    }

    setupService.setup(msg.accountId)

    sender.tell(AuthResponse(accountId = msg.accountId, response = LoginResponse.SUCCESS), self)
  }
}