package net.bestia.login.staticlogin

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.login.account.AccountRepository
import net.bestia.login.account.loginmethod.StaticTokenLoginMethodRepository
import net.bestia.login.jwt.JwtService
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Simple username + static token login intended for development. Validates the supplied
 * credentials against the seeded [net.bestia.login.account.loginmethod.StaticTokenLoginMethod]s and
 * issues a signed login JWT carrying the account role.
 */
@Service
class StaticLoginService(
  private val staticTokenLoginMethodRepository: StaticTokenLoginMethodRepository,
  private val accountRepository: AccountRepository,
  private val jwtService: JwtService
) {

  sealed class AuthResult {
    data class Success(val jwtToken: String) : AuthResult()
    data class Failure(val error: String) : AuthResult()
  }

  fun authenticate(username: String, token: String): AuthResult {
    val loginMethod = staticTokenLoginMethodRepository.findByUsername(username)
      ?: return AuthResult.Failure("Invalid credentials")

    if (loginMethod.staticToken != token) {
      return AuthResult.Failure("Invalid credentials")
    }

    val account = loginMethod.account

    val now = LocalDateTime.now()
    loginMethod.lastUsedAt = now
    staticTokenLoginMethodRepository.save(loginMethod)
    account.lastLogin = now
    accountRepository.save(account)

    val jwt = jwtService.createLoginToken(account.id, account.role)

    LOG.info { "Static login succeeded for user '$username' (account ${account.id}, role ${account.role})" }

    return AuthResult.Success(jwt)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
