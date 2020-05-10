package net.bestia.zoneserver.account

import net.bestia.zoneserver.actor.socket.LoginResponse
import org.springframework.stereotype.Service

@Service
class AuthenticationService(
    private val loginChecks: List<LoginCheck>
) {

  fun isUserAuthenticated(accountId: Long, token: String): LoginResponse {
    loginChecks.forEach {
      val status = it.isLoginAllowedForAccount(accountId, token)
      if (status != LoginResponse.SUCCESS) {
        return status
      }
    }

    return LoginResponse.SUCCESS
  }
}