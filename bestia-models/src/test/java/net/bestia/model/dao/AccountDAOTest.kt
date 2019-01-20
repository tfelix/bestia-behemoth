package net.bestia.model.dao

import net.bestia.model.account.AccountRepository
import net.bestia.model.test.AccountFixture
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest
@DataJpaTest
class AccountDAOTest {

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
