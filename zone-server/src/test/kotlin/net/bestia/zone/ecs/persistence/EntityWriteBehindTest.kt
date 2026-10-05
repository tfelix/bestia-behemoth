package net.bestia.zone.ecs.persistence

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EntityWriteBehindTest {

  /** Runs every job at once and remembers its key, so the grouping can be asserted. */
  private val keys = mutableListOf<Any>()
  private val executor = mockk<AsyncJobExecutor>().also {
    every { it.submit(any(), any()) } answers {
      keys.add(firstArg())
      secondArg<() -> Unit>().invoke()
    }
  }

  private val written = mutableListOf<List<EntitySnapshot>>()
  private val persister = object : EntityPersister {
    override val kind = "test"
    override val loadsAtStartup = false
    override fun supports(world: World, id: EntityId): Boolean {
      return true
    }

    override fun snapshot(world: World, id: EntityId): EntitySnapshot {
      return Snapshot(id, ownerKey = if (id % 2 == 0L) "owner-$id" else null)
    }

    override fun persist(snapshots: List<EntitySnapshot>) {
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

  private data class Snapshot(override val entityId: EntityId, val ownerKey: String?) : EntitySnapshot {
    override val writeKey: Any
      get() {
        return ownerKey ?: EntitySnapshot.SHARED_WRITE_KEY
      }
  }
}
