package net.bestia.zoneserver.client

import mu.KotlinLogging
import net.bestia.messages.account.AccountRegistration
import net.bestia.messages.account.AccountRegistrationError
import net.bestia.model.account.AccountRepository
import net.bestia.model.bestia.BestiaRepository
import net.bestia.model.dao.PlayerBestiaDAO
import net.bestia.model.dao.findOneOrThrow
import net.bestia.model.account.Account
import net.bestia.model.domain.Password
import net.bestia.model.domain.PlayerBestia
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val LOG = KotlinLogging.logger { }

/**
 * Generates all the needed account services. Please be careful: This factory is
 * not threadsafe. Therefore each thread should have its own AccountService.
 *
 * @author Thomas Felix
 */
@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
@Service
class AccountService(
    private val accountDao: AccountRepository,
    private val playerBestiaDao: PlayerBestiaDAO,
    private val bestiaDao: BestiaRepository
) {

  private fun getStarterBestiaId(): Int {
    return 1
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

    val acc = accountDao.findOneOrThrow(accId)

    // Abort if account is activated.
    if (acc.isActivated) {
      return false
    }

    return if (acc.loginToken == activationCode) {
      acc.isActivated = true
      acc.loginToken = ""
      true
    } else {
      false
    }
  }

  /**
   * Creates a completely new account. The username will be given to the
   * bestia master. No other bestia mastia can have this name.
   *
   */
  fun createNewAccount(data: AccountRegistration) {
    if (data.username.isEmpty()) {
      throw IllegalArgumentException("Mastername can not be null or empty.")
    }

    playerBestiaDao.findMasterBestiaWithName(data.username) ?: run {
      LOG.warn { "Can not create account. Master name already exists: ${data.username}" }
      throw AccountRegistrationException(AccountRegistrationError.USERNAME_INVALID)
    }

    val starterId = getStarterBestiaId()
    val origin = bestiaDao.findOneOrThrow(starterId)

    if (origin == null) {
      LOG.error("Starter bestia with id {} could not been found.", starterId)
      throw IllegalArgumentException("Starter bestia was not found.")
    }

    if (accountDao.findByEmail(data.email) != null) {
      LOG.debug("Could not create account because of duplicate mail: {}", data.email)
      throw AccountRegistrationException(AccountRegistrationError.EMAIL_INVALID)
    }

    val account = Account(
        email = data.email,
        password = Password(data.password),
        username = data.username,
        gender = data.gender,
        registerDate = Instant.now()
    )

    val masterBestia = PlayerBestia(
        owner = account,
        origin = origin,
        master = account
    ).also {
      it.name = data.username
    }

    // TODO Implement a proper account activation process.
    account.isActivated = true

    accountDao.save(account)
    playerBestiaDao.save(masterBestia)
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
    val acc = accountDao.findByUsername(username) ?: return null
    LOG.warn("Currently broken!")
    return acc
  }
}