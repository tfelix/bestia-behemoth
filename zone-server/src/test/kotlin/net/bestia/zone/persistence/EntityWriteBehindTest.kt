package net.bestia.zone.persistence

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import net.bestia.zone.ecs.persistence.StatusEffectPersistenceService

class EntityWriteBehindTest {

  /** Runs every job at once and remembers its key, so the grouping can be asserted. */
  private val keys = mutableListOf<Any>()
  private val executor = mockk<AsyncJobExecutor>().also {
    every { it.submit(any(), any()) } answers {
      keys.add(firstArg())
      // Like the executor: a failed job is logged, not thrown at the caller.
      runCatching { secondArg<() -> Unit>().invoke() }
    }
  }

  private val written = mutableListOf<List<EntitySnapshot>>()

  /** What each entity's state is at, so a test can change it; and how many writes should still fail. */
  private val versions = mutableMapOf<EntityId, Int>()
  private var failingWrites = 0

  private val persister = object : EntityPersister {
    override val kind = "test"
    override val loadsAtStartup = false
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

  private val statusEffects = mockk<StatusEffectPersistenceService>(relaxed = true).also {
    every { it.snapshot(any(), any()) } returns null
  }

  private val sut = EntityWriteBehind(listOf(persister), statusEffects, executor)

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
