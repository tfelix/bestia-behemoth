package net.bestia.zone.ecs.persistence

import net.bestia.zone.bestia.BestiaEntitySpawner
import net.bestia.zone.ecs.core.SnowflakeEntityIdGenerator
import net.bestia.zone.ecs.core.World
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pruning the rows of entities that have left the world for good.
 *
 * ### The bug this was written for
 *
 * `deleteByEntityIdIn` was a bulk `@Query("DELETE FROM PersistedEntity ...")`, which is precisely the form
 * `deleteAllByKind`'s KDoc has always warned against: a bulk statement bypasses the persistence context, so
 * it never cascades to the child `entity_component` rows and fails `fk_component_entity` the moment a
 * matching row has a blob. **Every mob row has one**, which is why the hazard was documented and live at the
 * same time - the warning sat on the neighbouring function.
 *
 * What made it expensive rather than merely wrong is where it sat. `pruneRemovedEntities` runs *first* in a
 * sync cycle, `drainAll` has already emptied the queue by then, and `scheduledSync` swallows the exception -
 * so from the first mob death onward every persistence sync aborted before writing anything, silently, and
 * the ids it meant to prune were gone with it.
 *
 * These cases all go through [EntityPersistenceService.syncOnce] rather than the repository, because the
 * ordering is the subject: the prune runs before the snapshot phase, and the failure was that breaking there
 * took the rest of the cycle with it.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class PersistedEntityDeletionTest {

  @Autowired
  private lateinit var bestiaEntitySpawner: BestiaEntitySpawner

  @Autowired
  private lateinit var mobEntityPersister: MobEntityPersister

  @Autowired
  private lateinit var persistedEntityRepository: PersistedEntityRepository

  @Autowired
  private lateinit var deletionQueue: PersistedEntityDeletionQueue

  @Autowired
  private lateinit var entityPersistenceService: EntityPersistenceService

  @BeforeEach
  fun clean() {
    persistedEntityRepository.deleteAll()
    deletionQueue.drainAll()
  }

  @Test
  fun `a queued entity's row and its component blob are both deleted`() {
    val world = newWorld()
    val doomed = spawn(world)
    persist(world, doomed)
    assertEquals(1, persistedEntityRepository.findAllByEntityIdIn(listOf(doomed)).size)

    deletionQueue.enqueue(doomed)
    entityPersistenceService.syncOnce()

    assertTrue(
      persistedEntityRepository.findAllByEntityIdIn(listOf(doomed)).isEmpty(),
      "the row survived the prune - the delete either failed or never cascaded to entity_component"
    )
  }

  @Test
  fun `a whole dormant pack is pruned in one cycle`() {
    // The shape `SpawnerSystem.despawnPack` produces: several rows at once, all of them with blobs. The bulk
    // statement failed on the first of these, which is why this is a batch rather than a single id.
    val world = newWorld()
    val pack = (1..4).map { spawn(world).also { id -> persist(world, id) } }
    assertEquals(4, persistedEntityRepository.findAllByEntityIdIn(pack).size)

    pack.forEach(deletionQueue::enqueue)
    entityPersistenceService.syncOnce()

    assertTrue(persistedEntityRepository.findAllByEntityIdIn(pack).isEmpty(), "part of the pack survived")
  }

  @Test
  fun `a batch mixing live rows with ids that have none still prunes the live ones`() {
    // Both real callers enqueue unconditionally - `DeathSystem` on any death, `SpawnerSystem` for a whole
    // dormant pack including members already destroyed this tick - so ids that never reached a sync cycle
    // are the normal case rather than an edge one, and they must not take the batch down with them.
    val world = newWorld()
    val doomed = spawn(world)
    persist(world, doomed)

    deletionQueue.enqueue(doomed)
    deletionQueue.enqueue(NEVER_PERSISTED)
    entityPersistenceService.syncOnce()

    assertTrue(persistedEntityRepository.findAllByEntityIdIn(listOf(doomed)).isEmpty())
  }

  @Test
  fun `a prune leaves the rows it was not asked about alone`() {
    val world = newWorld()
    val doomed = spawn(world)
    val keeper = spawn(world)
    persist(world, doomed)
    persist(world, keeper)

    deletionQueue.enqueue(doomed)
    entityPersistenceService.syncOnce()

    assertEquals(
      1,
      persistedEntityRepository.findAllByEntityIdIn(listOf(keeper)).size,
      "the prune deleted a row nobody queued"
    )
  }

  private fun spawn(world: World) =
    bestiaEntitySpawner.spawnMob(world, bestiaId = BLOB_BESTIA_ID, pos = Vec3L(1, 2, 3))

  private fun persist(world: World, entityId: EntityId) {
    val snapshot = world.read { mobEntityPersister.snapshot(this, entityId) }
    assertNotNull(snapshot)
    mobEntityPersister.persist(listOf(snapshot))
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

    /** An id no row was ever written for. */
    const val NEVER_PERSISTED = 123_456_789L
  }
}
