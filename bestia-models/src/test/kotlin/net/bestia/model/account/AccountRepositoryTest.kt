package net.bestia.model.account

import net.bestia.model.IntegrationTest
import net.bestia.model.test.AccountFixture
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTest
class AccountRepositoryTest {

  @Autowired
  private lateinit var accountDao: AccountRepository

  @Test
  fun findByEmail_test() {
    val a = AccountFixture.createAccount()
    accountDao.save(a)

    val found = accountDao.findByEmail(AccountFixture.email)
    assertNotNull(found)
  }

  @Test
  fun findByEmailOrUsername_findsBoth() {
    val a = AccountFixture.createAccount()
    accountDao.save(a)

    var found = accountDao.findByUsernameOrEmail(AccountFixture.username)
    assertNotNull(found)

    found = accountDao.findByUsernameOrEmail(AccountFixture.email)
    assertNotNull(found)
  }
}
