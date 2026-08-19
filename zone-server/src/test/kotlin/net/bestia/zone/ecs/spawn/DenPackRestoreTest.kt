package net.bestia.zone.ecs.spawn

import io.mockk.mockk
import net.bestia.zone.bestia.BestiaEntitySpawner
import net.bestia.zone.ecs.entity.EntityVisual
import net.bestia.zone.ecs.core.SnowflakeEntityIdGenerator
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.ecs.persistence.persisters.MobEntityPersister
import net.bestia.zone.entity.PersistedEntityRepository
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
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
 * A den's creatures survive a restart, and come back belonging to the same den.
 *
 * The bug this is the regression for was not a crash. Den mobs already persisted; what was missing was the
 * *link*. On every boot the den was rebuilt with a new entity id and an empty pack while its old creatures
 * were rehydrated as free-floating mobs nothing owned - so the den stocked a full pack on top of them and
 * the world's population grew with every restart, permanently, with nothing in any log to say so.
 *
 * Structured like `EntityPersistenceRoundTripTest`: isolated non-ticking [World]s so the real tick loop
 * cannot wander a blob mid-assertion, and a second fresh world standing in for the restart.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class DenPackRestoreTest {

  @Autowired
  private lateinit var bestiaEntitySpawner: BestiaEntitySpawner

  @Autowired
  private lateinit var mobEntityPersister: MobEntityPersister

  @Autowired
  private lateinit var persistedEntityRepository: PersistedEntityRepository

  private val deletionQueue = PersistedEntityDeletionQueue()

  /**
   * Mocked because the assertion is about which den a creature is attached to, not about what the system
   * then does with it - and a real one would need a cell index, a tick loop and a player.
   */
  private val spawnerSystem: SpawnerSystem = mockk(relaxed = true)

  private val service by lazy { DenPackRestoreService(spawnerSystem, deletionQueue) }

  @BeforeEach
  fun clean() {
    persistedEntityRepository.deleteAll()
    deletionQueue.drainAll()
  }

  @Test
  fun `a creature is handed back to the same den after a restart`() {
    val identity = identity(featureId = 7L)
    val creatureId = persistCreature(identity)

    val restarted = newWorld()
    val den = placeDen(restarted, identity)
    mobEntityPersister.loadAll(restarted)
    val result = service.restore(restarted)

    assertTrue(restarted.isAlive(creatureId), "the creature did not survive the restart")
    assertEquals(setOf(creatureId), den.spawnedEntities)
    assertEquals(1, result.reattached)
    assertEquals(0, result.discarded)
  }

  @Test
  fun `a restart does not double the population`() {
    // The headline. Before this change the den came back believing itself empty and made a second full pack
    // beside the first, so a three-creature den held six after one restart and nine after two.
    val identity = identity(featureId = 7L)
    val pack = (1..3).map { persistCreature(identity, x = it.toLong()) }

    val restarted = newWorld()
    val den = placeDen(restarted, identity, pack = 3)
    mobEntityPersister.loadAll(restarted)
    service.restore(restarted)

    assertEquals(pack.toSet(), den.spawnedEntities)
    assertEquals(3, countMobs(restarted), "the world holds more creatures than the den's pack size")

    // And again, on another fresh world - the `ScriptEntityPersisterTest` shape. A second restart must be
    // as uneventful as the first, or the inflation is merely slower.
    val secondRestart = newWorld()
    val secondDen = placeDen(secondRestart, identity, pack = 3)
    mobEntityPersister.loadAll(secondRestart)
    service.restore(secondRestart)

    assertEquals(3, secondDen.spawnedEntities.size)
    assertEquals(3, countMobs(secondRestart))
  }

  @Test
  fun `restoring twice is harmless`() {
    // The boot ordering makes a second call impossible today, but the service must not be the thing that
    // makes that ordering load bearing.
    val identity = identity(featureId = 7L)
    val creatureId = persistCreature(identity)

    val restarted = newWorld()
    val den = placeDen(restarted, identity)
    mobEntityPersister.loadAll(restarted)
    service.restore(restarted)
    val second = service.restore(restarted)

    assertTrue(restarted.isAlive(creatureId))
    assertEquals(setOf(creatureId), den.spawnedEntities)
    assertEquals(0, second.discarded)
  }

  @Test
  fun `a creature whose den no longer exists is discarded, not orphaned`() {
    // What a `candidateSpacing` retune looks like: the feature ids are a hash of a sequential ordinal, so
    // every den on the world is renamed and every stored name refers to nothing.
    val creatureId = persistCreature(identity(featureId = 42L))

    val restarted = newWorld()
    placeDen(restarted, identity(featureId = 99L))
    mobEntityPersister.loadAll(restarted)
    val result = service.restore(restarted)

    assertFalse(restarted.isAlive(creatureId), "an orphan was left alive in the world")
    assertEquals(1, result.discarded)
    assertTrue(creatureId in deletionQueue.drainAll(), "the orphan's row was never queued for deletion")
  }

  @Test
  fun `a creature is discarded when the world was regenerated under the same pipeline version`() {
    // The case a pipelineVersion-only guard misses, and the reason `DenIdentity` carries a world id: a
    // version is a digest of code and params with no seed in it, so a reseeded world wears the same one.
    val creatureId = persistCreature(DenIdentity(featureId = 7L, worldId = 1L, worldVersion = 100L))

    val restarted = newWorld()
    val den = placeDen(restarted, DenIdentity(featureId = 7L, worldId = 2L, worldVersion = 100L))
    mobEntityPersister.loadAll(restarted)
    val result = service.restore(restarted)

    assertFalse(restarted.isAlive(creatureId))
    assertTrue(den.spawnedEntities.isEmpty(), "a creature from another world was adopted by this one's den")
    assertEquals(1, result.discarded)
  }

  @Test
  fun `a creature is discarded when the den set was renamed by a params retune`() {
    val creatureId = persistCreature(DenIdentity(featureId = 7L, worldId = 1L, worldVersion = 100L))

    val restarted = newWorld()
    placeDen(restarted, DenIdentity(featureId = 7L, worldId = 1L, worldVersion = 200L))
    mobEntityPersister.loadAll(restarted)
    val result = service.restore(restarted)

    assertFalse(restarted.isAlive(creatureId))
    assertEquals(1, result.discarded)
  }

  @Test
  fun `a mob with no den survives a restart untouched`() {
    // `/spawn`ed mobs, and every row written before dens owned their packs. They are meant to persist and
    // nothing here may take them away.
    val spawnWorld = newWorld()
    val creatureId = bestiaEntitySpawner.spawnMob(spawnWorld, bestiaId = BLOB_BESTIA_ID, pos = Vec3L(1, 2, 3))
    persist(spawnWorld, creatureId)

    val restarted = newWorld()
    val den = placeDen(restarted, identity(featureId = 7L))
    mobEntityPersister.loadAll(restarted)
    val result = service.restore(restarted)

    assertTrue(restarted.isAlive(creatureId), "a den-less mob was destroyed by the den restore")
    assertEquals(0, result.reattached)
    assertEquals(0, result.discarded)
    assertEquals(1, result.unowned)
    assertTrue(den.spawnedEntities.isEmpty())
  }

  @Test
  fun `a pack larger than its den's current size is trimmed`() {
    // Pack sizes move: `wild-spawn` band multipliers and the generator's own params both change them. Left
    // alone the den would sit permanently over its size, and only a dormancy cycle would ever clear it.
    val identity = identity(featureId = 7L)
    val pack = (1..4).map { persistCreature(identity, x = it.toLong()) }

    val restarted = newWorld()
    val den = placeDen(restarted, identity, pack = 2)
    mobEntityPersister.loadAll(restarted)
    val result = service.restore(restarted)

    assertEquals(2, den.spawnedEntities.size)
    assertEquals(2, result.trimmed)
    assertEquals(2, pack.count { restarted.isAlive(it) })
  }

  // ------------------------------------------------------------------------------------------ fixtures

  private fun identity(featureId: Long) =
    DenIdentity(featureId = featureId, worldId = WORLD_ID, worldVersion = WORLD_VERSION)

  /** Spawns a creature owned by [identity] into a throwaway world and writes its row. */
  private fun persistCreature(identity: DenIdentity, x: Long = 1L): EntityId {
    val spawnWorld = newWorld()
    val creatureId = bestiaEntitySpawner.spawnMob(
      spawnWorld,
      bestiaId = BLOB_BESTIA_ID,
      pos = Vec3L(x, 2, 3),
      den = DenMember(identity)
    )
    persist(spawnWorld, creatureId)
    return creatureId
  }

  private fun persist(world: World, creatureId: EntityId) {
    val snapshot = world.read { mobEntityPersister.snapshot(this, creatureId) }
    assertNotNull(snapshot)
    mobEntityPersister.persist(listOf(snapshot))
  }

  private fun placeDen(world: World, identity: DenIdentity, pack: Int = 3): Spawner {
    val spawner = Spawner(
      identity = identity,
      bestiaId = BLOB_BESTIA_ID,
      maxSpawnCount = pack,
      position = Vec3L(0, 0, 0),
      range = 10
    )
    world.createEntity { id -> world.add(id, spawner) }
    return spawner
  }

  private fun countMobs(world: World): Int {
    var count = 0
    world.each(EntityVisual::class) { _, _ -> count++ }
    return count
  }

  /** An isolated, non-ticking world so systems (the blob wanders) can't perturb the assertions. */
  /**
   * One generator across every world this test builds. A fresh `SnowflakeEntityIdGenerator` restarts its
   * sequence at 0, so two of them created inside the same millisecond - which is what a loop of `newWorld()`
   * does - hand out the *same* id, and entities meant to be distinct collide in the persistence table.
   */
  private val idGenerator = SnowflakeEntityIdGenerator()

  private fun newWorld() = World(idGenerator = idGenerator, systems = emptyList())

  private companion object {
    // Seeded from mob/blob.yml by the mob importer in the test profile.
    const val BLOB_BESTIA_ID = 1L
    const val WORLD_ID = 1L
    const val WORLD_VERSION = 42L
  }
}
