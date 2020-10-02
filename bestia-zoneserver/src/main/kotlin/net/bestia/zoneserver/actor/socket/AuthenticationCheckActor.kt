package net.bestia.zoneserver.actor.socket

import akka.actor.AbstractActor
import akka.actor.Props
import net.bestia.messages.AccountMessage
import net.bestia.zoneserver.account.AuthenticationService

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
  NO_LOGINS_ALLOWED
}

/**
 * Tries to authenticate a client.
 */
class AuthenticationCheckActor(
    private val authenticationService: AuthenticationService
) : AbstractActor() {
  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(AuthRequest::class.java, this::authenticate)
        .build()
  }

  private fun authenticate(msg: AuthRequest) {
    val isAuthenticated = authenticationService.isUserAuthenticated(msg.accountId, msg.token)
    if (isAuthenticated != LoginResponse.SUCCESS) {
      sender.tell(AuthResponse(accountId = msg.accountId, response = isAuthenticated), self)
      return
    }

    sender.tell(AuthResponse(accountId = msg.accountId, response = LoginResponse.SUCCESS), self)
  }

  companion object {
    fun props(
        authenticationService: AuthenticationService
    ): Props {
      return Props.create(AuthenticationCheckActor::class.java) {
        AuthenticationCheckActor(authenticationService)
      }
    }
  }
}