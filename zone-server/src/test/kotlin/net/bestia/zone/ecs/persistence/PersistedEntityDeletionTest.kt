package net.bestia.zone.ecs.persistence

import net.bestia.zone.bestia.BestiaEntitySpawner
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.ecs.core.SnowflakeEntityIdGenerator
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.item.CarryCapacity
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.item.ObtainItemIntent
import net.bestia.zone.ecs.item.ObtainItemIntentSystem
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.persistence.persisters.LootItemEntityPersister
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.persistence.persisters.MobEntityPersister
import net.bestia.zone.persistence.PersistedEntityRepository
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.bestia.zone.persistence.EntityPersistenceService
import net.bestia.zone.persistence.EntitySnapshot
import net.bestia.zone.persistence.PersistedEntityDeletionQueue

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
 * sync cycle, `drainAll` has already emptied the queue by then, and the scheduled sync swallowed the exception -
 * so from the first mob death onward every persistence sync aborted before writing anything, silently, and
 * the ids it meant to prune were gone with it.
 *
 * These cases all go through [EntityPersistenceService.syncAll] rather than the repository, because the
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

  @Autowired
  private lateinit var lootItemEntitySpawner: LootItemEntitySpawner

  @Autowired
  private lateinit var lootItemEntityPersister: LootItemEntityPersister

  @Autowired
  private lateinit var obtainItemIntentSystem: ObtainItemIntentSystem

  @Autowired
  private lateinit var asyncJobExecutor: AsyncJobExecutor

  @Autowired
  private lateinit var liveWorld: WorldView

  /** Runs a cycle over the live world, which holds none of these entities, and waits for its writes. */
  private fun syncAndWait() {
    liveWorld.read { entityPersistenceService.syncAll(this) }
    asyncJobExecutor.awaitPending(EntitySnapshot.SHARED_WRITE_KEY)
  }

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
    syncAndWait()

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
    syncAndWait()

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
    syncAndWait()

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
    syncAndWait()

    assertEquals(
      1,
      persistedEntityRepository.findAllByEntityIdIn(listOf(keeper)).size,
      "the prune deleted a row nobody queued"
    )
  }

  @Test
  fun `a picked-up ground item's row is pruned`() {
    // A surviving row is rehydrated at the next boot, so the item could be picked up a second time.
    val world = EcsWorld(idGenerator = idGenerator, systems = listOf(obtainItemIntentSystem))
    val groundItem = lootItemEntitySpawner.spawnLootItem(world, itemId = APPLE_ITEM_ID, amount = 1, pos = Vec3L(1, 2, 3))
    val snapshot = world.read { lootItemEntityPersister.snapshot(this, groundItem) }
    assertNotNull(snapshot)
    lootItemEntityPersister.persist(listOf(snapshot))

    val looter = world.createEntity { id ->
      add(id, Inventory(mutableListOf()))
      add(id, CarryCapacity(current = 0, max = 1000))
      add(id, Position.fromVec3(Vec3L(1, 2, 3)))
      add(id, ObtainItemIntent.LootItemIntent(sourceEntityItemStackId = groundItem))
    }
    world.tick(0.1f)
    assertNotNull(world.get(looter, Inventory::class)?.getItem(APPLE_ITEM_ID.toInt()), "the pickup itself failed")

    syncAndWait()

    assertTrue(
      persistedEntityRepository.findAllByEntityIdIn(listOf(groundItem)).isEmpty(),
      "the picked-up item's row survived and would be rehydrated at the next boot"
    )
  }

  private fun spawn(world: World) =
    bestiaEntitySpawner.spawnMob(world, bestiaId = BLOB_BESTIA_ID, pos = Vec3L(1, 2, 3))

  private fun persist(world: World, entityId: EntityId) {
    val snapshot = mobEntityPersister.snapshot(world, entityId)
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

  private fun newWorld() = EcsWorld(idGenerator = idGenerator, systems = emptyList())

  private companion object {
    // Seeded from mob/blob.yml by the mob importer in the test profile.
    const val BLOB_BESTIA_ID = 1L

    // Seeded from items.yml.
    const val APPLE_ITEM_ID = 1L

    /** An id no row was ever written for. */
    const val NEVER_PERSISTED = 123_456_789L
  }
}
