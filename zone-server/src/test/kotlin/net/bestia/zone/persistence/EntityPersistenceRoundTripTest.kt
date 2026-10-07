package net.bestia.zone.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.ecs.core.SnowflakeEntityIdGenerator
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.GroundItemDecay
import net.bestia.zone.item.ecs.GroundItemIntegrity
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.item.persistence.LootItemEntityPersister
import net.bestia.zone.spawn.persistence.MobEntityPersister
import net.bestia.zone.spawn.ecs.DenIdentity
import net.bestia.zone.spawn.ecs.DenMember
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.loot.LootItemEntitySpawner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises the persist -> reload round trip for a world mob end-to-end against the real persister,
 * spawner and (in-memory) DB. Uses isolated [World] instances rather than the Spring-managed world
 * so the running tick loop (which would let the blob wander) can't make the assertions flaky.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class EntityPersistenceRoundTripTest {

  @Autowired
  private lateinit var bestiaEntitySpawner: BestiaEntitySpawner

  @Autowired
  private lateinit var mobEntityPersister: MobEntityPersister

  @Autowired
  private lateinit var lootItemEntitySpawner: LootItemEntitySpawner

  @Autowired
  private lateinit var lootItemEntityPersister: LootItemEntityPersister

  @Autowired
  private lateinit var persistedEntityRepository: PersistedEntityRepository

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @BeforeEach
  fun clean() {
    persistedEntityRepository.deleteAll()
  }

  @Test
  fun `mob is persisted and reloaded with the same id, position and current hp`() {
    val spawnWorld = newWorld()
    val pos = Vec3L(11, 22, 3)
    val entityId = bestiaEntitySpawner.spawnMob(spawnWorld, bestiaId = BLOB_BESTIA_ID, pos = pos)

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

  /** A restart must not give a dropped item its full time on the ground again. */
  @Test
  fun `ground item is reloaded with the despawn time it was dropped with`() {
    val spawnWorld = newWorld()
    val entityId = lootItemEntitySpawner.spawnLootItem(spawnWorld, itemId = 1L, amount = 3, pos = Vec3L(4, 5, 6))
    val despawnAt = spawnWorld.read { getOrThrow(entityId, GroundItemDecay::class).despawnAt }

    val snapshot = spawnWorld.read { lootItemEntityPersister.snapshot(this, entityId) }
    assertNotNull(snapshot)
    lootItemEntityPersister.persist(listOf(snapshot))

    val reloadWorld = newWorld()
    lootItemEntityPersister.loadAll(reloadWorld)

    reloadWorld.read {
      assertEquals(despawnAt, getOrThrow(entityId, GroundItemDecay::class).despawnAt)
    }
  }

  /** A restart must not mend a stack that a fire has half burnt. */
  @Test
  fun `ground item is reloaded with the integrity it had lost`() {
    val spawnWorld = newWorld()
    val entityId = lootItemEntitySpawner.spawnLootItem(spawnWorld, itemId = 1L, amount = 3, pos = Vec3L(4, 5, 6))
    spawnWorld.modify(entityId) { id -> getOrThrow(id, GroundItemIntegrity::class).lost = 7 }

    val snapshot = spawnWorld.read { lootItemEntityPersister.snapshot(this, entityId) }
    lootItemEntityPersister.persist(listOf(assertNotNull(snapshot)))

    val reloadWorld = newWorld()
    lootItemEntityPersister.loadAll(reloadWorld)

    reloadWorld.read {
      assertEquals(7, getOrThrow(entityId, GroundItemIntegrity::class).lost)
    }
  }

  @Test
  fun `a saved den creature comes back once, unsaved, and its row is queued for deletion`() {
    val spawnWorld = newWorld()
    val den = DenMember(DenIdentity(featureId = 7L, worldId = 1L, worldVersion = 1L))
    val entityId = bestiaEntitySpawner.spawnMob(spawnWorld, bestiaId = BLOB_BESTIA_ID, pos = Vec3L(1, 2, 3), den = den)
    val snapshot = spawnWorld.read { mobEntityPersister.snapshot(this, entityId) }
    mobEntityPersister.persist(listOf(assertNotNull(snapshot)))

    // Its own queue, because the running zone drains the shared one every second.
    val deletionQueue = PersistedEntityDeletionQueue()
    val reloadWorld = newWorld()
    MobEntityPersister(persistedEntityRepository, bestiaEntitySpawner, objectMapper, deletionQueue).loadAll(reloadWorld)

    assertTrue(reloadWorld.isAlive(entityId))
    assertFalse(reloadWorld.read { has(entityId, Persistent::class) }, "it would be saved again")
    assertEquals(listOf(entityId), deletionQueue.drainAll())
  }

  /** An isolated, non-ticking world so systems (the blob wanders) can't perturb the assertions. */
  /**
   * One generator across every world this test builds. A fresh `SnowflakeEntityIdGenerator` restarts its
   * sequence at 0, so two of them created inside the same millisecond - which is what a loop of `newWorld()`
   * does - hand out the *same* id, and entities meant to be distinct collide in the persistence table.
   */
  private val idGenerator = SnowflakeEntityIdGenerator()

  private fun newWorld() = EcsWorld(idGenerator = idGenerator, systems = emptyList())

  private companion object {
    // Seeded from mob/blob.yml by the mob importer in the test profile.
    const val BLOB_BESTIA_ID = 1L
  }
}
