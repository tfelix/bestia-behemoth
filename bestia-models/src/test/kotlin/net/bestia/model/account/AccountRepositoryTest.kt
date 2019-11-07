package net.bestia.model.account

import net.bestia.model.IntegrationTest
import net.bestia.model.test.AccountFixture
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTest
class AccountRepositoryTest {

  @Autowired
  private lateinit var accountRepository: AccountRepository

  @Test
  fun findByEmail_test() {
    AccountFixture.createAccount(accountRepository)

    val found = accountRepository.findByEmail(AccountFixture.email)
    assertNotNull(found)
  }
}
