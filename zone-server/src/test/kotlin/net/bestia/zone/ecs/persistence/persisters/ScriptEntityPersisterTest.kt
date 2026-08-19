package net.bestia.zone.ecs.persistence.persisters

import net.bestia.zone.ecs.core.SnowflakeEntityIdGenerator
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.script.ScriptComponent
import net.bestia.zone.entity.PersistedEntityRepository
import net.bestia.zone.entity.deleteAllByKind
import net.bestia.zone.world.MasterSpawnPointService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises [ScriptEntityPersister.loadAll]'s double duty: creating one script entity per settlement
 * spawn point candidate when none are persisted yet, and rehydrating those exact entity ids - not
 * duplicating them - on a later "restart". Uses isolated [World] instances rather than the
 * Spring-managed world, same as [net.bestia.zone.ecs.persistence.EntityPersistenceRoundTripTest].
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class ScriptEntityPersisterTest {

  @Autowired
  private lateinit var scriptEntityPersister: ScriptEntityPersister

  @Autowired
  private lateinit var masterSpawnPointService: MasterSpawnPointService

  @Autowired
  private lateinit var persistedEntityRepository: PersistedEntityRepository

  @BeforeEach
  fun clean() {
    persistedEntityRepository.deleteAllByKind(ScriptComponent.KIND)
  }

  @Test
  fun `creates one script entity per spawn point, and a simulated restart rehydrates the same ids without duplicating`() {
    val spawnPoints = masterSpawnPointService.ensureComputed()
    assertTrue(spawnPoints.isNotEmpty(), "no settlement spawn point candidates were computed")

    val firstBoot = newWorld()
    scriptEntityPersister.loadAll(firstBoot)

    val rowsAfterFirstBoot = persistedEntityRepository.findAllByKind(ScriptComponent.KIND)
    assertEquals(spawnPoints.size, rowsAfterFirstBoot.size)

    val idsAfterFirstBoot = rowsAfterFirstBoot.map { it.entityId }.toSet()
    idsAfterFirstBoot.forEach { id ->
      assertTrue(firstBoot.isAlive(id), "script entity $id was not spawned into the world")
    }

    // Simulate a restart: a fresh world, loadAll called again.
    val secondBoot = newWorld()
    scriptEntityPersister.loadAll(secondBoot)

    val rowsAfterSecondBoot = persistedEntityRepository.findAllByKind(ScriptComponent.KIND)
    assertEquals(
      idsAfterFirstBoot,
      rowsAfterSecondBoot.map { it.entityId }.toSet(),
      "restart duplicated script entities instead of rehydrating the persisted ones"
    )
    idsAfterFirstBoot.forEach { id ->
      assertTrue(secondBoot.isAlive(id), "script entity $id was not rehydrated")
    }
  }

  /**
   * One generator across every world this test builds. A fresh `SnowflakeEntityIdGenerator` restarts its
   * sequence at 0, so two of them created inside the same millisecond - which is what a loop of `newWorld()`
   * does - hand out the *same* id, and entities meant to be distinct collide in the persistence table.
   */
  private val idGenerator = SnowflakeEntityIdGenerator()

  private fun newWorld() = World(idGenerator = idGenerator, systems = emptyList())
}
