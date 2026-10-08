package net.bestia.zone.account.persistence

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.scenarios.ScenarioDataSetup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals

@SpringBootTest
@ActiveProfiles("no-socket", "test")
class MasterRehomeTest {

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var testFixture: ScenarioDataSetup.TestFixture

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  @Test
  fun `a master of the fallen town gets the new home and stays where they stand`() {
    val masterId = testFixture.account3.masterIds.first()
    val transaction = TransactionTemplate(transactionManager)
    val before = transaction.execute { masterRepository.findByIdOrThrow(masterId).let { Home(it) } }!!

    try {
      transaction.executeWithoutResult { masterRepository.findByIdOrThrow(masterId).homeSettlementName = FALLEN }

      val moved = masterRepository.rehome(FALLEN, "New Town", 1, 2, 3)

      val after = transaction.execute { masterRepository.findByIdOrThrow(masterId).let { Home(it) } }!!
      assertEquals(1, moved)
      assertEquals(Home("New Town", Vec3L(1, 2, 3), before.current), after)
    } finally {
      // The fixture masters are shared with other test classes.
      transaction.executeWithoutResult {
        masterRepository.findByIdOrThrow(masterId).apply {
          homeSettlementName = before.name
          spawnPosition = before.spawn
        }
      }
    }
  }

  private data class Home(val name: String, val spawn: Vec3L, val current: Vec3L) {
    constructor(master: Master) : this(master.homeSettlementName, master.spawnPosition, master.currentPosition)
  }

  private companion object {
    const val FALLEN = "Fallen Test Town"
  }
}
