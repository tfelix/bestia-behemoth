package net.bestia.zoneserver.client

import net.bestia.messages.account.AccountRegistration
import net.bestia.messages.account.AccountRegistrationError
import net.bestia.model.dao.AccountDAO
import net.bestia.model.dao.BestiaDAO
import net.bestia.model.dao.PlayerBestiaDAO
import net.bestia.model.domain.*
import org.slf4j.LoggerFactory
import org.springframework.orm.jpa.JpaSystemException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Generates all the needed account services. Please be careful: This factory is
 * not threadsafe. Therefore each thread should have its own AccountService.
 *
 * @author Thomas Felix
 */
@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
@Service
class AccountService(
        private val accountDao: AccountDAO,
        private val playerBestiaDao: PlayerBestiaDAO,
        private val bestiaDao: BestiaDAO) {

  private fun getIdFromPlayerClass(pc: PlayerClass): Int {
    return 1
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

// Dont allow login for not activated accounts.
    if (!acc.isActivated) {
      return null
    }

    if (!acc.password.matches(password)) {
      return null
    }

    acc.lastLogin = Date()
    acc.loginToken = UUID.randomUUID().toString()
    accountDao.save(acc)
    return acc
  }

  /**
   * Checks if an account is not activated yet and then it will check if the
   * activation code matches the login token and if yes activate the account.
   *
   * @param activationCode
   * The activation code for this account.
   * @return TRUE if successfull. FALSE otherwise.
   */
  fun activateAccount(accId: Long, activationCode: String): Boolean {

    val acc = accountDao.findOne(accId)

    // Abort if account is activated.
    if (acc.isActivated) {
      return false
    }

    if (acc.loginToken == activationCode) {
      acc.isActivated = true
      acc.loginToken = ""
      return true
    } else {
      return false
    }
  }

  /**
   * Creates a completely new account. The username will be given to the
   * bestia master. No other bestia mastia can have this name.
   *
   * @return `TRUE` if the new account coule be created. `FALSE`
   * otherwise.
   */
  fun createNewAccount(data: AccountRegistration): AccountRegistrationError {

    val mastername = data.username

    if (mastername == null || mastername.isEmpty()) {
      throw IllegalArgumentException("Mastername can not be null or empty.")
    }

    val account = Account(data.email, data.password)

    // TODO das hier noch auslagern. Die aktivierung soll nur per
    // username/password anmeldung notwendig sein.
    account.isActivated = true

    // Depending on the master get the offspring bestia.
    val starterId = getIdFromPlayerClass(data.playerClass)
    val origin = bestiaDao.findOne(starterId)

    if (origin == null) {
      LOG.error("Starter bestia with id {} could not been found.", starterId)
      throw IllegalArgumentException("Starter bestia was not found.")
    }

    if (accountDao.findByEmail(data.email) != null) {
      LOG.debug("Could not create account because of duplicate mail: {}", data.email)
      return AccountRegistrationError.EMAIL_INVALID
    }

    // Check if there is a bestia master with this name.
    val existingMaster = playerBestiaDao.findMasterBestiaWithName(mastername)

    if (existingMaster != null) {
      LOG.warn("Can not create account. Master name already exists: {}", mastername)
      return AccountRegistrationError.USERNAME_INVALID
    }

    // Create the bestia.
    val masterBestia = PlayerBestia(account, origin, BaseValues.getStarterIndividualValues())

    masterBestia.name = mastername
    masterBestia.master = account

    // Generate ID.
    try {

      accountDao.save(account)
      playerBestiaDao.save(masterBestia)

      return AccountRegistrationError.NONE

    } catch (ex: JpaSystemException) {
      LOG.warn("Could not create account: {}", ex.message, ex)
      return AccountRegistrationError.GENERAL_ERROR
    }

  }

  /**
   * Returns accounts via their username (bestia master name), but only if
   * they are online. If the account is currently not logged in then null is
   * returned.
   *
   * @param username
   * The bestia master name to look for.
   * @return The [Account] of this bestia master or null if the name
   * does not exist or the account is not online.
   */
  fun getOnlineAccountByName(username: String): Account? {
    Objects.requireNonNull(username)
    val acc = accountDao.findByUsername(username) ?: return null

// Check if this account is online.
    /*
    if (connectionService.isConnected(acc.getId())) {
      return null;
    }*/
    LOG.warn("Currently broken!")

    return acc
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
    Objects.requireNonNull(accountName)
    Objects.requireNonNull(oldPassword)
    Objects.requireNonNull(newPassword)

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

  companion object {

    private val LOG = LoggerFactory.getLogger(AccountService::class.java)
  }

}