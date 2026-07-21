package net.bestia.zone.account.master

import net.bestia.zone.account.master.persistence.MasterEntityPersistenceService
import net.bestia.zone.ecs.battle.exp.Exp
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.scenarios.ScenarioDataSetup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

/**
 * Local proof-of-concept for the [net.bestia.zone.ecs.persistence.ComponentEntityWriter] / [EntityPersistenceService] shape —
 * remove alongside [net.bestia.zone.account.master.persistence.ExpComponentMasterWriter] / [net.bestia.zone.account.master.persistence.MasterEntityPersistenceService] once verified.
 *
 * Exercises two things that only show up when this is wired through real Spring, not just
 * "does it compile": (1) whether the generic-aware `List<ComponentPersistWriter<*, Master>>`
 * constructor injection picks up [net.bestia.zone.account.master.persistence.ExpComponentMasterWriter] correctly, and (2) whether the
 * `@Transactional` on [EntityPersistenceService.persistEntity] (declared on the abstract base,
 * inherited by [net.bestia.zone.account.master.persistence.MasterEntityPersistenceService]) actually gets proxied when called externally.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class MasterTestPersistenceServiceTest {

  @Autowired
  private lateinit var masterEntityPersistenceService: MasterEntityPersistenceService

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var worldView: WorldView

  @Autowired
  private lateinit var testFixture: ScenarioDataSetup.TestFixture

  @Test
  fun `persistEntity loads the master row, applies the world component via its writer, and saves it`() {
    val masterId = testFixture.account1.masterIds.first()
    val entityId = worldView.createEntity { id -> add(id, Exp(4321)) }

    masterEntityPersistenceService.persistEntity(worldView, entityId, masterId)

    val reloaded = masterRepository.findByIdOrThrow(masterId)
    assertEquals(4321, reloaded.exp)
  }
}
