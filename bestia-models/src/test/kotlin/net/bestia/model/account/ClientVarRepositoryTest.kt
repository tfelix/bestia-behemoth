package net.bestia.model.account

import net.bestia.model.test.AccountFixture
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@SpringBootTest
@DataJpaTest
class ClientVarRepositoryTest {

  @Autowired
  private lateinit var cvDao: ClientVarRepository

  @Autowired
  private lateinit var accDao: AccountRepository

  @BeforeEach
  fun setup() {
    val acc = AccountFixture.createAccount()
    accDao.save(acc)

    val cv = ClientVar(acc, EXISTING_KEY)
    cv.setData("Test123")
    cvDao.save(cv)
  }

  @Test
  fun findByKeyAndAccountId_validKeyAndAccId_finds() {
    val cv = cvDao.findByKeyAndAccountId(EXISTING_KEY, ACC_ID)
    assertNotNull(cv)
  }

  @Test
  fun deleteByKeyAndAccountId_validKeyAndAccId_deletes() {
    cvDao.deleteByKeyAndAccountId(EXISTING_KEY, ACC_ID)
    val cv = cvDao.findByKeyAndAccountId(EXISTING_KEY, ACC_ID)
    assertNull(cv)
  }

  companion object {
    private const val ACC_ID: Long = 1337
    private const val EXISTING_KEY = "test"
  }
}
