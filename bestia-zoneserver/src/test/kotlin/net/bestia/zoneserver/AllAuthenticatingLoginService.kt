package net.bestia.zoneserver

import net.bestia.zoneserver.account.LoginCheck
import net.bestia.zoneserver.actor.socket.LoginResponse

class AllAuthenticatingLoginService : LoginCheck {
  override fun isLoginAllowedForAccount(accountId: Long, token: String): LoginResponse {
    return LoginResponse.SUCCESS
  }
}