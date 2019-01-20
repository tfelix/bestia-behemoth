package net.bestia.model.dao

import net.bestia.model.test.AccountFixture
import net.bestia.model.test.BestiaFixture
import net.bestia.model.bestia.ConditionValues
import net.bestia.model.bestia.PlayerBestiaDAO
import net.bestia.model.bestia.PlayerBestia
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
class PlayerBestiaDAOTest {

  @Autowired
  private lateinit var playerDao: PlayerBestiaDAO

  @Before
  fun setup() {

    val acc = AccountFixture.createAccount()
    val pb = PlayerBestia(
        owner = acc,
        origin = BestiaFixture.bestia
    )

    val sv = ConditionValues()
    sv.currentHealth = 10
    sv.currentMana = 10

    pb.conditionValues = sv
    pb.level = 10

    playerDao.save(pb)
  }

  @Test
  fun findPlayerBestiasForAccount_unknownAcc_null() {
    val bestias = playerDao.findPlayerBestiasForAccount(1337)
    Assert.assertTrue(bestias.size == 0)
  }

  @Test
  fun findPlayerBestiasForAccount_knownAcc_bestias() {
    val bestias = playerDao.findPlayerBestiasForAccount(1337)
    Assert.assertTrue(bestias.size == 0)
  }

  @Test
  fun findMasterBestiaWithName_knownName_bestia() {
    val pb = playerDao.findMasterBestiaWithName(BESTIA_NAME)

    Assert.assertNotNull(pb)
  }

  @Test
  fun findMasterBestiaWithName_unknownName_null() {
    val pb = playerDao.findMasterBestiaWithName(BESTIA_UNKNOWN_NAME)
    Assert.assertNull(pb)
  }

  companion object {
    private const val BESTIA_NAME = "test"
    private const val BESTIA_UNKNOWN_NAME = "blablitest"
  }
}
