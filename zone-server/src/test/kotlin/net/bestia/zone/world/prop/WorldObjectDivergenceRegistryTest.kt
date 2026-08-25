package net.bestia.zone.world.prop

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The two orphan guards, which are the only reason a stored `propId` can be trusted.
 *
 * A row surviving into a world it does not belong to is silently wrong rather than loudly absent - a tree
 * felled in one world staying felled where that `propId` names different ground - so each guard gets a test
 * that fails if it stops discarding.
 */
class WorldObjectDivergenceRegistryTest {

  private val thisLattice = 42L
  private val thisShape = 7L

  private fun registry(
    rows: List<WorldObjectDivergence>,
    lattice: Long = thisLattice,
    shape: Long = thisShape
  ): Pair<WorldObjectDivergenceRegistry, WorldObjectDivergenceRepository> {
    val repository = mockk<WorldObjectDivergenceRepository>(relaxed = true) {
      every { findAll() } returns rows
    }
    val worldService = mockk<WorldService> {
      every { record } returns mockk {
        every { pipelineVersion } returns lattice
        every { shapeVersion } returns shape
      }
    }

    // Runs the job inline. A relaxed mock would swallow it, and the durable-write assertions below would
    // then pass against a `save` that never happened.
    val executor = mockk<AsyncJobExecutor> {
      every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
    }

    return WorldObjectDivergenceRegistry(repository, executor, worldService) to repository
  }

  private fun row(propId: Long, lattice: Long = thisLattice, shape: Long = thisShape) =
    WorldObjectDivergence(propId, StaticEntityKind.TREE.name, DivergenceState.DEPLETED, lattice, shape)

  @Test
  fun `a row matching both versions is loaded`() {
    val (sut, repository) = registry(listOf(row(1L)))

    sut.loadAll()

    assertNotNull(sut.of(1L))
    verify(exactly = 0) { repository.deleteAll(any<List<WorldObjectDivergence>>()) }
  }

  @Test
  fun `a row from a moved lattice is discarded, not merely skipped`() {
    val (sut, repository) = registry(listOf(row(1L, lattice = thisLattice - 1)))

    sut.loadAll()

    assertNull(sut.of(1L))
    verify { repository.deleteAll(any<List<WorldObjectDivergence>>()) }
  }

  /**
   * The guard this class spent its life without. `pipelineVersion` folds stage and params versions but **not
   * the seed**, so a reseeded world matched on the only guard there was and every row survived into terrain
   * that had nothing to do with it.
   */
  @Test
  fun `a row from a reseeded world is discarded even though its lattice still matches`() {
    val (sut, repository) = registry(listOf(row(1L, shape = thisShape - 1)))

    sut.loadAll()

    assertNull(sut.of(1L))
    verify { repository.deleteAll(any<List<WorldObjectDivergence>>()) }
  }

  @Test
  fun `a mixed table keeps only the rows belonging to this world`() {
    val (sut, _) = registry(
      listOf(
        row(1L),
        row(2L, lattice = thisLattice - 1),
        row(3L, shape = thisShape - 1),
        row(4L)
      )
    )

    sut.loadAll()

    assertNotNull(sut.of(1L))
    assertNull(sut.of(2L))
    assertNull(sut.of(3L))
    assertNotNull(sut.of(4L))
  }

  /** Both stamps come off one `record`, so a written row cannot carry a mismatched pair. */
  @Test
  fun `a recorded depletion is stamped with both of this world's versions`() {
    val (sut, repository) = registry(emptyList())
    val saved = slot<WorldObjectDivergence>()
    every { repository.save(capture(saved)) } answers { saved.captured }

    sut.recordDepletion(99L, StaticEntityKind.TREE, resumeAt = null)

    assertEquals(thisLattice, saved.captured.latticeVersion)
    assertEquals(thisShape, saved.captured.worldShapeVersion)
  }
}
