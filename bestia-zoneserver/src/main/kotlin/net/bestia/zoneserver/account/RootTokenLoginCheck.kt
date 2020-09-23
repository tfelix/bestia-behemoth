package net.bestia.zoneserver.account

import mu.KotlinLogging
import net.bestia.model.account.AccountRepository
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.actor.socket.LoginResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

@Order(Int.MIN_VALUE)
@Component
@ConditionalOnProperty("zone.root-auth-token", matchIfMissing = false)
class RootTokenLoginCheck(
    private val authConfig: AuthenticationConfig
) : LoginCheck {
  override fun isLoginAllowedForAccount(accountId: Long, token: String): LoginResponse? {
    return if (authConfig.rootAuthToken == token) {
      LOG.warn { "Account ID $accountId with root token ${token.take(5)}*** authenticated" }
      LoginResponse.SUCCESS
    } else {
      return null
    }
  }
}