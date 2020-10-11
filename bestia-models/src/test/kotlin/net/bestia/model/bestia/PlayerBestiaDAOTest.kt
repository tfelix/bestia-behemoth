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
    val bestias = playerRepo.findAllByOwnerId(1337)
    assertTrue(bestias.isEmpty())
  }

  @Test
  fun findPlayerBestiasForAccount_knownAcc_bestias() {
    setupDatabase()
    val bestias = playerRepo.findAllByOwnerId(1337)
    assertTrue(bestias.size == 0)
  }
}
