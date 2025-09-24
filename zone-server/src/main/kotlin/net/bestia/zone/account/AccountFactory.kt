package net.bestia.zone.account

import org.springframework.stereotype.Component

/**
 * Used to generate a fully new account and prepare all the required data so the player can log in.
 * Must also select and place the newly generated master on a good spot in the world.
 */
@Component
class AccountFactory(
  private val accountRepository: AccountRepository
) {

  fun createAccount(
    loginAccountId: Long
  ): Account {
    val account = Account(loginAccountId)

    return accountRepository.save(account)
  }
}