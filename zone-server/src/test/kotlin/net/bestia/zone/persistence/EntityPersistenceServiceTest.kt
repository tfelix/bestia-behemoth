package net.bestia.zone.persistence

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
  private var queueFull = false
  private val executor = mockk<AsyncJobExecutor>(relaxed = true).also {
    every { it.trySubmit(any(), any()) } answers { !queueFull }
  }

  private val sut = EntityPersistenceService(
    writeBehind = writeBehind,
    asyncJobExecutor = executor,
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

  @Test
  fun `rows whose delete the executor dropped are queued again`() {
    queueFull = true
    deletionQueue.enqueue(REMOVED)

    sut.syncAll(testWorld())

    assertEquals(1, deletionQueue.pending, "the id waits for the next sweep, or its rows would stay for good")
  }

  @Test
  fun `rows queued for deletion are no longer waiting`() {
    deletionQueue.enqueue(REMOVED)
    assertEquals(1, deletionQueue.pending)

    sut.syncAll(testWorld())

    assertEquals(0, deletionQueue.pending)
  }

  private companion object {
    const val SWEEPS = 9
    const val REMOVED = 77L
  }
}
