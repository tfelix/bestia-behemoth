package net.bestia.zoneserver.account

import net.bestia.zoneserver.actor.socket.LoginResponse
import org.springframework.stereotype.Service

@Service
class AuthenticationService(
    private val loginChecks: List<LoginCheck>
) {

  fun isUserAuthenticated(accountId: Long, token: String): LoginResponse {
    for (loginCheck in loginChecks) {
      val loginResult = loginCheck.isLoginAllowedForAccount(accountId, token)
          ?: continue

      if (loginResult == LoginResponse.SUCCESS) {
        return loginResult
      }
    }

    return LoginResponse.UNAUTHORIZED
  }
}