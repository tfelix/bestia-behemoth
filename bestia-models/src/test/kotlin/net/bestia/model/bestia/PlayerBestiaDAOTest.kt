package net.bestia.model.bestia

import net.bestia.model.IntegrationTest
import net.bestia.model.account.AccountRepository
import net.bestia.model.test.AccountFixture
import net.bestia.model.test.BestiaFixture
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTest
class PlayerBestiaDAOTest {

  @Autowired
  private lateinit var playerRepo: PlayerBestiaRepository

  @Autowired
  private lateinit var accountRepo: AccountRepository

  @Autowired
  private lateinit var bestiaRepository: BestiaRepository

  fun setupDatabase() {
    val acc = AccountFixture.createAccount(accountRepo)

    val pb = PlayerBestia(
        owner = acc,
        origin = BestiaFixture.createBestia(bestiaRepository)
    )

    val sv = ConditionValues(
        currentHealth = 10,
        currentMana = 10
    )

    pb.conditionValues = sv
    pb.level = 10

    playerRepo.save(pb)
  }

  @Test
  fun findPlayerBestiasForAccount_unknownAcc_null() {
    setupDatabase()
    val bestias = playerRepo.findPlayerBestiasForAccount(1337)
    assertTrue(bestias.isEmpty())
  }

  @Test
  fun findPlayerBestiasForAccount_knownAcc_bestias() {
    setupDatabase()
    val bestias = playerRepo.findPlayerBestiasForAccount(1337)
    assertTrue(bestias.size == 0)
  }

  @Test
  fun findMasterBestiaWithName_knownName_bestia() {
    setupDatabase()
    val pb = playerRepo.findMasterBestiaWithName(BESTIA_NAME)

    assertNotNull(pb)
  }

  @Test
  fun findMasterBestiaWithName_unknownName_null() {
    setupDatabase()
    val pb = playerRepo.findMasterBestiaWithName(BESTIA_UNKNOWN_NAME)
    assertNull(pb)
  }

  companion object {
    private const val BESTIA_NAME = "test"
    private const val BESTIA_UNKNOWN_NAME = "blablitest"
  }
}
