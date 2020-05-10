package net.bestia.zoneserver.account

import net.bestia.zoneserver.actor.socket.LoginResponse

interface LoginCheck {
  fun isLoginAllowedForAccount(accountId: Long, token: String): LoginResponse
}