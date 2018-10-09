package net.bestia.model.dao

import net.bestia.model.test.AccountFixture
import net.bestia.model.domain.ClientVar
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest
@DataJpaTest
class ClientVarDAOTest {

  @Autowired
  private lateinit var cvDao: ClientVarDAO

  @Autowired
  private lateinit var accDao: AccountDAO

  @Before
  fun setup() {
    val acc = AccountFixture.createAccount()
    acc.id = ACC_ID
    accDao.save(acc)

    val cv = ClientVar(acc, EXISTING_KEY, "test1234")
    cvDao.save(cv)
  }

  @Test
  fun findByKeyAndAccountId_validKeyAndAccId_finds() {
    val cv = cvDao.findByKeyAndAccountId(EXISTING_KEY, ACC_ID)
    Assert.assertNotNull(cv)
  }

  @Test
  fun deleteByKeyAndAccountId_validKeyAndAccId_deletes() {
    cvDao.deleteByKeyAndAccountId(EXISTING_KEY, ACC_ID)
    val cv = cvDao.findByKeyAndAccountId(EXISTING_KEY, ACC_ID)
    Assert.assertNull(cv)
  }

  companion object {
    private const val ACC_ID: Long = 1337
    private const val EXISTING_KEY = "test"
  }
}
