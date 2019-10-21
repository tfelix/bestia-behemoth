package net.bestia.model.battle

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener

@ExtendWith(SpringExtension::class)
@SpringBootTest
@TestExecutionListeners(DependencyInjectionTestExecutionListener::class)
@DataJpaTest
class AttackLevelRepositoryTest {
  @Autowired
  private lateinit var attackLevelDao: BestiaAttackRepository

  @Test
  fun getAllAttacksForBestia_existingId_list() {
    val atks = attackLevelDao.getAllAttacksForBestia(1)
    assertNotNull(atks)
    assertEquals(1, atks.size.toLong())
  }

  @Test
  fun getAllAttacksForBestia_notExistingId_null() {
    val atks = attackLevelDao.getAllAttacksForBestia(1337)
    assertTrue(atks.isEmpty())
  }
}
