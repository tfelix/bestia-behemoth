package net.bestia.model.battle

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener

import com.github.springtestdbunit.DbUnitTestExecutionListener
import com.github.springtestdbunit.annotation.DatabaseSetup

@RunWith(SpringRunner::class)
@SpringBootTest
@TestExecutionListeners(DependencyInjectionTestExecutionListener::class, DbUnitTestExecutionListener::class)
@DatabaseSetup("/db/attack_levels.xml")
@DataJpaTest
class AttackLevelRepositoryTest {

  @Autowired
  private lateinit var attackLevelDao: BestiaAttackRepository

  @Test
  fun getAllAttacksForBestia_existingId_list() {
    val atks = attackLevelDao.getAllAttacksForBestia(1)
    Assert.assertNotNull(atks)
    Assert.assertEquals(1, atks.size.toLong())
  }

  @Test
  fun getAllAttacksForBestia_notExistingId_null() {
    val atks = attackLevelDao.getAllAttacksForBestia(1337)
    Assert.assertTrue(atks.isEmpty())

  }

}
