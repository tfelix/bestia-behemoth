package net.bestia.loginserver.account

import net.bestia.loginserver.error.BestiaError
import net.bestia.loginserver.error.BestiaHttpException
import net.bestia.model.account.Account
import net.bestia.model.account.AccountRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

@Component
class AccountFactory(
    private val accountRepository: AccountRepository
) {

  fun createAccount(newAccount: CreateAccountRequestV1): Account {
    val account = Account(
        username = newAccount.username,
        gender = newAccount.gender,
        hairstyle = newAccount.hairstyle
    )

    try {
      accountRepository.save(account)
    } catch (e: DataIntegrityViolationException) {
      throw BestiaHttpException(
          httpCode = HttpStatus.CONFLICT,
          errorCode = BestiaError.REGISTER_ACCOUNT_USERNAME_USED
      )
    }

    return account
  }
}