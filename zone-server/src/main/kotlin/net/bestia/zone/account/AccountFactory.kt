package net.bestia.zone.account

import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Component

/**
 * Used to generate a fully new account and prepare all the required data so the player can log in.
 * Must also select and place the newly generated master on a good spot in the world.
 */
@Component
class AccountFactory(
  private val accountRepository: AccountRepository
) {

  /**
   * Creates the zone-local row for [accountId], which is the login-server account id the client
   * authenticated with — see [Account] for why the zone does not mint its own.
   */
  fun createAccount(
    accountId: AccountId,
  ): Account {
    val account = Account(accountId)

    return accountRepository.save(account)
  }
}
