package net.bestia.zone.ecs.persistence

import net.bestia.zone.bestia.BestiaEntityFactory
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.core.SnowflakeEntityIdGenerator
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.persistence.persisters.MobEntityPersister
import net.bestia.zone.entity.PersistedEntityRepository
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises the persist -> reload round trip for a world mob end-to-end against the real persister,
 * factory and (in-memory) DB. Uses isolated [World] instances rather than the Spring-managed world
 * so the running tick loop (which would let the blob wander) can't make the assertions flaky.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class EntityPersistenceRoundTripTest {

  @Autowired
  private lateinit var bestiaEntityFactory: BestiaEntityFactory

  @Autowired
  private lateinit var mobEntityPersister: MobEntityPersister

  @Autowired
  private lateinit var persistedEntityRepository: PersistedEntityRepository

  @BeforeEach
  fun clean() {
    persistedEntityRepository.deleteAll()
  }

  @Test
  fun `mob is persisted and reloaded with the same id, position and current hp`() {
    val spawnWorld = newWorld()
    val pos = Vec3L(11, 22, 3)
    val entityId = bestiaEntityFactory.createMobEntity(spawnWorld, bestiaId = BLOB_BESTIA_ID, pos = pos)

    // Damage the mob so we can prove current HP (mutable state) round-trips, not just the full template value.
    spawnWorld.modify(entityId) { id -> getOrThrow(id, Health::class).current = 3 }

    // Snapshot under the lock + persist — exactly what one batch of the periodic service does.
    val snapshot = spawnWorld.read { mobEntityPersister.snapshot(this, entityId) }
    assertNotNull(snapshot)
    mobEntityPersister.persist(listOf(snapshot))

    assertEquals(1, persistedEntityRepository.findAllByEntityIdIn(listOf(entityId)).size)

    // Fresh world simulating a server restart; rehydrate from storage.
    val reloadWorld = newWorld()
    mobEntityPersister.loadAll(reloadWorld)

    assertTrue(reloadWorld.isAlive(entityId), "reloaded entity keeps its persisted id")
    reloadWorld.read {
      assertEquals(pos, getOrThrow(entityId, Position::class).toVec3L())
      assertEquals(3, getOrThrow(entityId, Health::class).current)
    }
  }

  /** An isolated, non-ticking world so systems (the blob wanders) can't perturb the assertions. */
  private fun newWorld() = World(idGenerator = SnowflakeEntityIdGenerator(), systems = emptyList())

  private companion object {
    // Seeded from mob/blob.yml by the mob importer in the test profile.
    const val BLOB_BESTIA_ID = 1L
  }
}
