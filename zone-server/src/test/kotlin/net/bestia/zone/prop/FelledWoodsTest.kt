package net.bestia.zone.prop

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.ground.ColumnKey
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.persistence.DivergenceState
import net.bestia.zone.prop.persistence.WorldObjectDivergenceRepository
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.time.BestiaClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FelledWoodsTest {

  private val worldService: WorldService = mockk {
    every { record } returns mockk {
      every { pipelineVersion } returns 1L
      every { shapeVersion } returns 1L
    }
  }

  private val divergence = WorldObjectDivergenceRegistry(
    mockk<WorldObjectDivergenceRepository>(relaxed = true) { every { save(any()) } answers { firstArg() } },
    mockk<AsyncJobExecutor> { every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() } },
    worldService
  )

  // Prop ids below 100 stand in column (0, 0), the rest in column (1, 0).
  private val trees: TreeNeighbourhood = mockk {
    every { columnOf(any()) } answers { ChunkPos(if (firstArg<Long>() < 100) 0 else 1, 0, 0) }
  }

  private val clock: BestiaClock = mockk { every { speedFactor } returns 3.0 }

  private val kinds = PropKindRegistry().also { it.load() }

  private fun index() = FelledWoods(divergence, kinds, trees, clock)

  private fun record(propId: Long, state: DivergenceState, kind: StaticEntityKind = StaticEntityKind.TREE) {
    divergence.record(propId, kind, state, resumeAt = null)
  }

  @Test
  fun `a stump is a whole felled tree, a young one half of one, a grown one none`() {
    val woods = index()

    record(1, DivergenceState.STUMP)
    record(2, DivergenceState.SAPLING)
    record(3, DivergenceState.YOUNG)
    record(4, DivergenceState.GROWN)

    assertEquals(2.5, woods.felledIn(COLUMN_A), 1e-9)
  }

  @Test
  fun `a tree is followed as it grows back, until its column is clear again`() {
    val woods = index()

    record(1, DivergenceState.STUMP)
    record(2, DivergenceState.YOUNG)
    divergence.evictRegrown(1)
    record(2, DivergenceState.GROWN)

    assertEquals(emptyList<Long>(), woods.felledColumns())
  }

  @Test
  fun `a picked herb is not a felled tree`() {
    val woods = index()

    record(1, DivergenceState.DEPLETED, StaticEntityKind.HERB)

    assertEquals(emptyList<Long>(), woods.felledColumns())
  }

  @Test
  fun `trees felled before the index existed are counted, each in its own column`() {
    record(1, DivergenceState.STUMP)
    record(100, DivergenceState.STUMP)

    val woods = index()

    assertEquals(1.0, woods.felledIn(COLUMN_A))
    assertEquals(1.0, woods.felledIn(ColumnKey.of(1, 0)))
  }

  @Test
  fun `a tree takes three real weeks to grow back, which is sixty-three days at three times speed`() {
    assertEquals(63.0, index().regrowthDays(), 1e-9)
  }

  @Test
  fun `a grown tree yields four logs`() {
    assertEquals(4.0, index().yieldPerTree(LOG))
  }

  private companion object {
    val COLUMN_A = ColumnKey.of(0, 0)
    const val LOG = 59L
  }
}
