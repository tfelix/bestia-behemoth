package net.bestia.zoneserver.account

import mu.KotlinLogging
import net.bestia.model.account.AccountRepository
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.actor.socket.LoginResponse
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

@Component
class AuthenticationLoginCheck(
    private val authConfig: AuthenticationConfig,
    private val accountRepository: AccountRepository
) : LoginCheck {
  override fun isLoginAllowedForAccount(accountId: Long, token: String): LoginResponse {
    if (token.isEmpty()) {
      return LoginResponse.UNAUTHORIZED
    }

    if (authConfig.rootAuthToken == token && authConfig.rootAuthToken != null) {
      LOG.debug { "Account ID $accountId with token ${token.take(5)}*** authenticated" }
      return LoginResponse.SUCCESS
    }

    val account = accountRepository.findOneOrThrow(accountId)
    if (account.loginToken == token) {
      LOG.debug { "Account ID $accountId with token ${token.take(5)}*** authenticated" }
      return LoginResponse.SUCCESS
    }

    LOG.debug { "Account ID $accountId with token ${token.take(5)}*** not authenticated" }
    return LoginResponse.UNAUTHORIZED
  }
}