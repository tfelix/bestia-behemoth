package net.bestia.zone.ecs.persistence

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EntityPersistenceServiceTest {

  private val saved = mutableListOf<EntityId>()
  private val writeBehind = mockk<EntityWriteBehind>(relaxed = true).also {
    every { it.persist(any(), any(), any(), any()) } answers { saved.addAll(secondArg<Collection<EntityId>>()) }
  }
  private val deletionQueue = PersistedEntityDeletionQueue()

  private val sut = EntityPersistenceService(
    writeBehind = writeBehind,
    asyncJobExecutor = mockk(relaxed = true),
    deletionQueue = deletionQueue,
    persistedEntityRepository = mockk(relaxed = true),
    statusEffectPersistenceService = mockk(relaxed = true),
  )

  @Test
  fun `one interval saves every persistent entity exactly once, a slice per sweep`() {
    val world = testWorld()
    val persistent = (1..50).map { world.createEntity { id -> add(id, Persistent) } }
    world.createEntity { }

    repeat(SWEEPS) { sweep -> sut.syncDue(world, sweep.toLong(), SWEEPS.toLong()) }

    assertEquals(persistent.sorted(), saved.sorted())
  }

  @Test
  fun `the periodic save only writes what changed`() {
    val world = testWorld()
    world.createEntity { id -> add(id, Persistent) }

    sut.syncAll(world)

    verify { writeBehind.persist(world, any(), any(), onlyChanged = true) }
  }

  @Test
  fun `a removed entity is forgotten as its row is pruned`() {
    deletionQueue.enqueue(REMOVED)

    sut.syncAll(testWorld())

    verify { writeBehind.forget(listOf(REMOVED)) }
  }

  private companion object {
    const val SWEEPS = 9
    const val REMOVED = 77L
  }
}
