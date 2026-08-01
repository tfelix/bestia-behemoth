package net.bestia.worldgen.pipeline

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.Parallel
import net.bestia.worldgen.core.WorldConfig
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The guard rail on every parallel loop in the module: splitting the work must not change the world.
 *
 * `StandardWorldTest.the world is reproducible from its seed` already pins run-to-run reproducibility, but
 * it runs the same code twice, so it cannot see a split that is wrong in a *consistent* way - the classic
 * one being a per-thread partial sum, which is perfectly reproducible on one machine and gives a different
 * answer on a machine with a different core count. That failure would surface as a client generating base
 * terrain that does not match the server's, months later, with no way to tell which of the two was right.
 *
 * So the property asserted here is stronger than reproducibility: the number of workers must be invisible.
 * A build on one thread, on two, and on all of them must agree bit for bit.
 *
 * If this fails, the loop that broke it is not a pure per-cell gather, whatever its comment says. Look for
 * an accumulator, a shared scratch buffer, or a read of a cell another band writes - and note that a
 * `Grid.blur`-style double buffer is only safe while nothing reads `data` during the pass.
 */
class ParallelDeterminismTest {

  private fun config(seed: Long) = WorldConfig(
    seed = seed,
    // Above Parallel.MIN_CELLS, so the loops actually split rather than falling back to serial and
    // agreeing trivially. 96x96 is 9216 cells against a threshold of 8192.
    widthCells = 96,
    heightCells = 96,
    chunkSize = 32,
    voxelSize = 1.0
  )

  private fun build(seed: Long) = StandardWorld.build(config(seed))

  private fun assertSameWorld(a: GeneratedWorld, b: GeneratedWorld, what: String) {
    assertEquals(a.world.pipelineVersion, b.world.pipelineVersion, "$what: pipeline version")

    val ids = a.world.layers.ids().toList()
    assertEquals(ids, b.world.layers.ids().toList(), "$what: layer set")

    for (id in ids) {
      when (val layer = a.world.layers[id]) {
        is FloatLayer ->
          assertTrue(
            layer.data.contentEquals((b.world.layers[id] as FloatLayer).data),
            "$what: $id differs"
          )

        is IntLayer ->
          assertTrue(
            layer.data.contentEquals((b.world.layers[id] as IntLayer).data),
            "$what: $id differs"
          )

        else -> error("unexpected layer type for $id")
      }
    }

    assertEquals(a.world.features.size, b.world.features.size, "$what: feature count")
    assertEquals(
      a.world.features.all().map { it.id },
      b.world.features.all().map { it.id },
      "$what: feature ids or their order"
    )
  }

  @Test
  fun `the parallel world is the serial world`() {
    // Skipped rather than passed when there is nothing to split - on a single-core machine, or under the
    // `-Pserial` switch that exists to answer "did the splitting cause this failure". A comparison of
    // serial against serial would pass and mean nothing, which is worse than not running.
    assumeTrue(
      Parallel.enabled && Parallel.threads > 1 && Parallel.bandsFor(96, 96L * 96) > 1,
      "nothing would be split here, so the comparison would be vacuous"
    )

    val parallel = build(7L)
    val serial = Parallel.serially { build(7L) }

    assertSameWorld(serial, parallel, "serial vs parallel")
  }

  @Test
  fun `two parallel builds agree`() {
    // Catches the split that is unstable rather than merely wrong: a shared scratch buffer usually
    // produces a slightly different world each run, which the serial comparison above might blame on the
    // split being wrong in principle when it is in fact a race.
    assertSameWorld(build(11L), build(11L), "parallel vs parallel")
  }

  @Test
  fun `two serial builds agree`() {
    // The control. If this fails the defect is not in the splitting at all.
    assertSameWorld(Parallel.serially { build(13L) }, Parallel.serially { build(13L) }, "serial vs serial")
  }
}
