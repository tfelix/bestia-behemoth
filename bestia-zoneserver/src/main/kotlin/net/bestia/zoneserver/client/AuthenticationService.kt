package net.bestia.zoneserver.client

import mu.KotlinLogging
import net.bestia.model.account.AccountRepository
import net.bestia.model.findOneOrThrow
import net.bestia.model.account.Account
import net.bestia.model.domain.Password
import net.bestia.model.server.MaintenanceLevel
import net.bestia.zoneserver.configuration.RuntimeConfigService
import org.springframework.stereotype.Service
import java.util.*

private val LOG = KotlinLogging.logger { }

@Service
class AuthenticationService(
    private val config: RuntimeConfigService,
    private val accountDao: AccountRepository
) {

  fun isUserAuthenticated(token: String): Boolean {
    return true
  }

  /**
   * This will return a [Account] with the needed, new access token. If
   * the wrong credentials where provided null is returned instead.
   *
   * @param accName
   * @return The account with the new token, or null if wrong credentials.
   */
  fun createLoginToken(accName: String, password: String): Account? {
    val acc = accountDao.findByEmail(accName) ?: return null

    if (!acc.isActivated) {
      return null
    }

    if (!acc.password.matches(password)) {
      return null
    }

    acc.loginToken = UUID.randomUUID().toString()
    accountDao.save(acc)
    return acc
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

    val acc = accountDao.findOneOrThrow(accId)

    if (acc.loginToken.isEmpty()) {
      LOG.debug("Login with empty token is not allowed.")
      return false
    }

    if (acc.loginToken != token) {
      LOG.trace("Account {} logintoken does not match.", accId)
      return false
    }

    // Special handling of maintenance mode.
    if (config.maintenanceMode != MaintenanceLevel.NONE) {

      // Depending on maintenance mode certain users can login.
      if (config.maintenanceMode == MaintenanceLevel.FULL) {
        LOG.debug("No accounts can login during full maintenance.")
        return false
      }

      if (config.maintenanceMode == MaintenanceLevel.PARTIAL && acc.userLevel.compareTo(Account.AccountType.SUPER_GM) < 0) {
        LOG.debug("Account {} can not login during maintenance User level too low.", accId)
        return false
      }
    }
    LOG.trace("Account {} login permitted.", accId)
    return true
  }

  /**
   * Sets the password without checking the old password first.
   *
   * @param accountName
   * @param newPassword
   * @return
   */
  fun changePasswordWithoutCheck(accountName: String, newPassword: String): Boolean {
    Objects.requireNonNull(accountName)
    Objects.requireNonNull(newPassword)

    val acc = accountDao.findByUsernameOrEmail(accountName) ?: return false

    acc.password = Password(newPassword)
    accountDao.save(acc)
    return true
  }

  /**
   * Tries to change the password for the given account. The old password must
   * match first before this method executes.
   */
  fun changePassword(accountName: String, oldPassword: String, newPassword: String): Boolean {
    if (newPassword.isEmpty()) {
      return false
    }

    val acc = accountDao.findByUsernameOrEmail(accountName) ?: return false

    val password = acc.password

    if (!password.matches(oldPassword)) {
      return false
    }

    acc.password = Password(newPassword)
    accountDao.save(acc)
    return true
  }
}