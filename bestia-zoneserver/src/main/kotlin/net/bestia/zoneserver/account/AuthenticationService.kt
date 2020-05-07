package net.bestia.zoneserver.account

import mu.KotlinLogging
import net.bestia.model.account.AccountRepository
import net.bestia.model.findOneOrThrow
import net.bestia.model.account.AccountType
import net.bestia.model.server.MaintenanceLevel
import net.bestia.zoneserver.config.RuntimeConfigService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.*

private val LOG = KotlinLogging.logger { }

@Service
class AuthenticationService(
    private val configService: RuntimeConfigService,
    private val authConfig: AuthenticationConfig,
    private val accountRepository: AccountRepository,
    private val passwordEncoder: PasswordEncoder
) {

  fun isUserAuthenticated(accountId: Long, token: String): Boolean {
    if(authConfig.rootAuthToken == token && authConfig.rootAuthToken != null) {
      LOG.debug { "Account ID $accountId with token ${token.take(5)}*** authenticated" }
      return true
    }

    LOG.debug { "Account ID $accountId with token ${token.take(5)}*** not authenticated" }
    return false
  }

  /**
   * An user can only login if he provides the correct login token and the
   * server is not in maintenance mode. A game master can override the server
   * maintenance mode flag.
   *
   * @param accId
   * The account ID to check if the login.
   * @param token
   * The token of this account id.
   * @return TRUE if the account is permitted to login FALSE otherwise.
   */
  @Deprecated("User are now directly logged in via web")
  fun canLogin(accId: Long, token: String): Boolean {
    Objects.requireNonNull(token)

    LOG.debug("Checking login for account {}.", accId)

    val acc = accountRepository.findOneOrThrow(accId)

    if (acc.loginToken.isEmpty()) {
      LOG.debug("Login with empty token is not allowed.")
      return false
    }

    if (acc.loginToken != token) {
      LOG.trace("Account {} logintoken does not match.", accId)
      return false
    }

    // Special handling of maintenance mode.
    val config = configService.getRuntimeConfig()
    if (config.maintenanceLevel != MaintenanceLevel.NONE) {

      // Depending on maintenance mode certain users can login.
      if (config.maintenanceLevel == MaintenanceLevel.FULL) {
        LOG.debug("No accounts can login during full maintenance.")
        return false
      }

      if (config.maintenanceLevel == MaintenanceLevel.PARTIAL && acc.userLevel < AccountType.SUPER_GM) {
        LOG.debug("Account {} can not login during maintenance User level too low.", accId)
        return false
      }
    }
    LOG.trace("Account {} login permitted.", accId)
    return true
  }
}