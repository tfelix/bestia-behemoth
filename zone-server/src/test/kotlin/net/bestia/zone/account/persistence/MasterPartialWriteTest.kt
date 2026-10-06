package net.bestia.zone.account.persistence

import net.bestia.zone.scenarios.ScenarioDataSetup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.awt.Color
import kotlin.test.assertEquals

/**
 * The master persister and a few other writers (party, inventory) save the same `master` row. A writer that
 * changes one column must not write back the others as it read them, or it undoes what the persister saved
 * in between.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class MasterPartialWriteTest {

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var testFixture: ScenarioDataSetup.TestFixture

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  @Test
  fun `a write of one column keeps a level another writer committed after the read`() {
    val masterId = testFixture.account3.masterIds.first()
    val outer = TransactionTemplate(transactionManager)
    val inner = TransactionTemplate(transactionManager).apply {
      propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }
    val before = outer.execute { masterRepository.findByIdOrThrow(masterId).let { it.level to it.hairColor } }!!

    try {
      outer.executeWithoutResult {
        val stale = masterRepository.findByIdOrThrow(masterId)

        inner.executeWithoutResult { masterRepository.findByIdOrThrow(masterId).level = before.first + 1 }

        stale.hairColor = Color.MAGENTA
      }

      val after = outer.execute { masterRepository.findByIdOrThrow(masterId) }!!
      assertEquals(before.first + 1, after.level)
      assertEquals(Color.MAGENTA, after.hairColor)
    } finally {
      // The fixture masters are shared with other test classes.
      outer.executeWithoutResult {
        masterRepository.findByIdOrThrow(masterId).apply {
          level = before.first
          hairColor = before.second
        }
      }
    }
  }
}
