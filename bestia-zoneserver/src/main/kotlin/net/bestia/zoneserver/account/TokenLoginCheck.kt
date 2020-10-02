package net.bestia.zoneserver.account

import mu.KotlinLogging
import net.bestia.model.account.AccountRepository
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.actor.socket.LoginResponse
import org.springframework.core.annotation.Order
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

@Order(1)
@Component
class TokenLoginCheck(
    private val accountRepository: AccountRepository
) : LoginCheck {
  override fun isLoginAllowedForAccount(accountId: Long, token: String): LoginResponse? {
    val account = accountRepository.findByIdOrNull(accountId)
        ?: return LoginResponse.UNAUTHORIZED

    return if (account.loginToken == token) {
      LOG.debug { "Account ID $accountId with token ${token.take(5)}*** authenticated" }
      LoginResponse.SUCCESS
    } else {
      LOG.debug { "Account ID $accountId with token ${token.take(5)}*** not authenticated" }
      LoginResponse.UNAUTHORIZED
    }
  }
}