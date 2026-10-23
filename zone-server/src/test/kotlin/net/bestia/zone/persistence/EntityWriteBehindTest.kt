package net.bestia.zone.persistence

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EntityWriteBehindTest {

  /** Runs every job at once and remembers its key, so the grouping can be asserted. */
  private val keys = mutableListOf<Any>()
  private var queueFull = false
  private val executor = mockk<AsyncJobExecutor>().also {
    every { it.trySubmit(any(), any()) } answers {
      keys.add(firstArg())
      if (queueFull) return@answers false

      // Like the executor: a failed job is logged, not thrown at the caller.
      runCatching { secondArg<() -> Unit>().invoke() }
      true
    }
  }

  private val meters = SimpleMeterRegistry()

  private fun entities(outcome: String): Double {
    return meters.get("zone.persist.entities").tag("outcome", outcome).counter().count()
  }

  private val written = mutableListOf<List<EntitySnapshot>>()

  /** What each entity's state is at, so a test can change it; and how many writes should still fail. */
  private val versions = mutableMapOf<EntityId, Int>()
  private var failingWrites = 0

  private val persister = object : EntityPersister {
    override val kind = "test"
    override val loadsAtStartup = false
    override val reads: ComponentClassSet = emptySet()
    override fun supports(world: World, id: EntityId): Boolean {
      return true
    }

    override fun snapshot(world: World, id: EntityId): EntitySnapshot {
      return Snapshot(id, ownerKey = if (id % 2 == 0L) "owner-$id" else null, version = versions[id] ?: 0)
    }

    override fun persist(snapshots: List<EntitySnapshot>) {
      if (failingWrites > 0) {
        failingWrites--
        error("database unavailable")
      }
      written.add(snapshots)
    }

    override fun loadAll(world: World) = Unit
  }

  private val statusEffects = mockk<EntitySidecar>(relaxed = true).also {
    every { it.reads } returns emptySet()
    every { it.snapshot(any(), any()) } returns null
  }

  private val sut = EntityWriteBehind(listOf(persister), statusEffects, executor, PersistenceMetrics(meters))

  @Test
  fun `snapshots sharing a key are written in one job, each owner in its own`() {
    val world = testWorld()

    sut.persist(world, listOf(1L, 2L, 3L, 4L))

    assertEquals(3, keys.size, "one job per key")
    assertEquals(setOf(EntitySnapshot.SHARED_WRITE_KEY, "owner-2", "owner-4"), keys.toSet())
    assertEquals(listOf(1L, 3L), written.first { batch -> batch.size == 2 }.map { it.entityId })
  }

  @Test
  fun `status effects can be left out`() {
    val world = testWorld()

    sut.persist(world, listOf(1L), withStatusEffects = false)

    verify(exactly = 0) { statusEffects.snapshot(any(), any()) }
  }

  @Test
  fun `a periodic save skips an entity that has not changed`() {
    val world = testWorld()

    sut.persist(world, listOf(1L), onlyChanged = true)
    sut.persist(world, listOf(1L), onlyChanged = true)
    versions[1L] = 1
    sut.persist(world, listOf(1L), onlyChanged = true)

    assertEquals(listOf(0, 1), written.flatten().map { (it as Snapshot).version })
  }

  /** A logout or an exp gain is written whatever the periodic save last saw. */
  @Test
  fun `a save that is not periodic always writes`() {
    val world = testWorld()

    sut.persist(world, listOf(1L))
    sut.persist(world, listOf(1L))

    assertEquals(2, written.size)
  }

  @Test
  fun `a failed write is retried by the next periodic save`() {
    val world = testWorld()
    failingWrites = 1

    sut.persist(world, listOf(1L), onlyChanged = true)
    sut.persist(world, listOf(1L), onlyChanged = true)

    assertEquals(1, written.size, "the unchanged entity was written again because the first write failed")
  }

  @Test
  fun `a forgotten entity counts as changed`() {
    val world = testWorld()
    sut.persist(world, listOf(1L), onlyChanged = true)

    sut.forget(listOf(1L))
    sut.persist(world, listOf(1L), onlyChanged = true)

    assertEquals(2, written.size)
  }

  @Test
  fun `a write the executor dropped is not remembered as saved`() {
    val world = testWorld()
    queueFull = true

    sut.persist(world, listOf(1L), onlyChanged = true)
    queueFull = false
    sut.persist(world, listOf(1L), onlyChanged = true)

    assertEquals(1, written.size, "the entity was written once the queue had room, though it had not changed")
    assertEquals(1.0, entities("rejected"))
  }

  @Test
  fun `what is waiting for the database is counted until its write ends`() {
    val world = testWorld()
    queueFull = true

    sut.persist(world, listOf(1L, 3L))

    assertEquals(0.0, meters.get("zone.persist.pending").gauge().value(), "a dropped write is not waiting")
    assertEquals(0.0, meters.get("zone.persist.oldest.age").gauge().value())
  }

  @Test
  fun `entities are counted by what became of them`() {
    val world = testWorld()
    failingWrites = 1

    sut.persist(world, listOf(1L), onlyChanged = true)
    sut.persist(world, listOf(1L), onlyChanged = true)
    sut.persist(world, listOf(1L), onlyChanged = true)

    assertEquals(2.0, entities("queued"))
    assertEquals(1.0, entities("failed"))
    assertEquals(1.0, entities("written"))
    assertEquals(1.0, entities("unchanged"))
    assertEquals(0.0, meters.get("zone.persist.pending").gauge().value())
  }

  private data class Snapshot(
    override val entityId: EntityId,
    val ownerKey: String?,
    val version: Int = 0,
  ) : EntitySnapshot {
    override val writeKey: Any
      get() {
        return ownerKey ?: EntitySnapshot.SHARED_WRITE_KEY
      }
  }
}
