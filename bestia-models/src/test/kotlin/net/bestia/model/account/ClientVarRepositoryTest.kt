package net.bestia.model.account

import net.bestia.model.IntegrationTest
import net.bestia.model.test.AccountFixture
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTest
class ClientVarRepositoryTest {

  @Autowired
  private lateinit var cvDao: ClientVarRepository

  @Autowired
  private lateinit var accountRepository: AccountRepository

  private lateinit var account: Account

  @BeforeEach
  fun setup() {
    account = AccountFixture.createAccount(accountRepository)

    val cv = ClientVar(account, EXISTING_KEY)
    cv.setData("Test123")
    cvDao.save(cv)
  }

  @Test
  fun findByKeyAndAccountId_validKeyAndAccId_finds() {
    val cv = cvDao.findByKeyAndAccountId(EXISTING_KEY, account.id)
    assertNotNull(cv)
  }

  @Test
  fun deleteByKeyAndAccountId_validKeyAndAccId_deletes() {
    cvDao.deleteByKeyAndAccountId(EXISTING_KEY, account.id)
    val cv = cvDao.findByKeyAndAccountId(EXISTING_KEY, account.id)
    assertNull(cv)
  }

  companion object {
    private const val EXISTING_KEY = "test"
  }
}
