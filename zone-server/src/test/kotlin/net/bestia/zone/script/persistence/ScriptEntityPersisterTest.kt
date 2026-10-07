package net.bestia.zone.script.persistence

import net.bestia.zone.ecs.core.SnowflakeEntityIdGenerator
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.World
import net.bestia.zone.script.ecs.ScriptComponent
import net.bestia.zone.persistence.PersistedEntityRepository
import net.bestia.zone.persistence.deleteAllByKind
import net.bestia.zone.world.MasterSpawnPointService
import net.bestia.zone.world.persistence.MasterSpawnPointRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises [ScriptEntityPersister.loadAll]'s double duty: raising one ward stone per settlement spawn point
 * that never had one, and rehydrating those exact entity ids - not duplicating them - on a later "restart".
 * Uses isolated [World] instances rather than the Spring-managed world, same as
 * [net.bestia.zone.persistence.EntityPersistenceRoundTripTest].
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

  @Autowired
  private lateinit var masterSpawnPointRepository: MasterSpawnPointRepository

  @BeforeEach
  fun clean() {
    persistedEntityRepository.deleteAllByKind(ScriptComponent.KIND)
    masterSpawnPointRepository.saveAll(masterSpawnPointService.ensureComputed().onEach { it.wardRaised = false })
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

  @Test
  fun `a ward stone destroyed for good is not raised again`() {
    scriptEntityPersister.loadAll(newWorld())

    // What DeathSystem's deletion queue does to the row of a stone that died.
    persistedEntityRepository.deleteAllByKind(ScriptComponent.KIND)
    scriptEntityPersister.loadAll(newWorld())

    assertTrue(persistedEntityRepository.findAllByKind(ScriptComponent.KIND).isEmpty())
  }

  @Test
  fun `stones standing on points that are not flagged yet are not doubled`() {
    scriptEntityPersister.loadAll(newWorld())
    val stonesBefore = persistedEntityRepository.findAllByKind(ScriptComponent.KIND).map { it.entityId }.toSet()

    masterSpawnPointRepository.saveAll(masterSpawnPointService.ensureComputed().onEach { it.wardRaised = false })
    scriptEntityPersister.loadAll(newWorld())

    assertEquals(stonesBefore, persistedEntityRepository.findAllByKind(ScriptComponent.KIND).map { it.entityId }.toSet())
    assertTrue(masterSpawnPointService.ensureComputed().all { it.wardRaised })
  }

  /**
   * One generator across every world this test builds. A fresh `SnowflakeEntityIdGenerator` restarts its
   * sequence at 0, so two of them created inside the same millisecond - which is what a loop of `newWorld()`
   * does - hand out the *same* id, and entities meant to be distinct collide in the persistence table.
   */
  private val idGenerator = SnowflakeEntityIdGenerator()

  private fun newWorld() = EcsWorld(idGenerator = idGenerator, systems = emptyList())
}
