package net.bestia.model.bestia

import net.bestia.model.IntegrationTest
import net.bestia.model.test.AccountFixture
import net.bestia.model.test.BestiaFixture
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTest
class PlayerBestiaDAOTest {

  @Autowired
  private lateinit var playerDao: PlayerBestiaRepository

  @BeforeEach
  fun setup() {

    val acc = AccountFixture.createAccount()
    val pb = PlayerBestia(
        owner = acc,
        origin = BestiaFixture.bestia
    )

    val sv = ConditionValues(
        currentHealth = 10,
        currentMana = 10
    )

    pb.conditionValues = sv
    pb.level = 10

    playerDao.save(pb)
  }

  @Test
  fun findPlayerBestiasForAccount_unknownAcc_null() {
    val bestias = playerDao.findPlayerBestiasForAccount(1337)
    assertTrue(bestias.size == 0)
  }

  @Test
  fun findPlayerBestiasForAccount_knownAcc_bestias() {
    val bestias = playerDao.findPlayerBestiasForAccount(1337)
    assertTrue(bestias.size == 0)
  }

  @Test
  fun findMasterBestiaWithName_knownName_bestia() {
    val pb = playerDao.findMasterBestiaWithName(BESTIA_NAME)

    assertNotNull(pb)
  }

  @Test
  fun findMasterBestiaWithName_unknownName_null() {
    val pb = playerDao.findMasterBestiaWithName(BESTIA_UNKNOWN_NAME)
    assertNull(pb)
  }

  companion object {
    private const val BESTIA_NAME = "test"
    private const val BESTIA_UNKNOWN_NAME = "blablitest"
  }
}
