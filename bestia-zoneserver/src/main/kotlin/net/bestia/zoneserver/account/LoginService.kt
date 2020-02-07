package net.bestia.zoneserver.account

interface LoginService {
  fun isLoginAllowedForAccount(accountId: Long): Boolean
}