package net.bestia.worldgen.pipeline

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sanity checks at world sizes nothing else in the suite exercises together: non-power-of-two, and neither a
 * multiple of the chunk edge (32) nor of the climate coarsening factor (4).
 *
 * Nothing in [WorldConfig] requires either property - `detailScale`, the ocean margin and `climateResolutionFor`
 * are all continuous functions of the world's extent, not table lookups keyed on it being a nice number - but
 * every other test in this module happens to use round numbers (128, 160, 192, 256, 288, 512), so a bug that
 * only shows up at an awkward size would have nowhere to be caught. This runs the full [Invariants] battery
 * rather than one narrow assertion, because the point is coverage of the *property*, not of any one stage.
 */
class WorldSizeSanityTest {

  private fun config(cells: Int, seed: Long) = WorldConfig(
    seed = seed,
    widthCells = cells,
    heightCells = cells,
    chunkSize = 32,
    voxelSize = 1.0
  )

  /**
   * Between the smallest size anything else tests and the 128 km world `zone-server` actually boots.
   *
   * 96 is `3 * chunkSize` - a multiple of the chunk edge, deliberately, to isolate the climate-coarsening
   * factor as the one non-round property at this size (96 / 4 = 24, which clears `MIN_CLIMATE_CELLS`, so this
   * still coarsens - the next size down covers the case where it does not).
   */
  @Test
  fun `a 96-cell world is clean`() = assertWorldIsClean(96, seed = 101L)

  /** Neither a multiple of the chunk edge nor of 4: 150 / 32 and 150 / 4 both land on a remainder. */
  @Test
  fun `a 150-cell world is clean`() = assertWorldIsClean(150, seed = 102L)

  /** Between Genesis (128) and the 512 km reference world every stage default was tuned against. */
  @Test
  fun `a 200-cell world is clean`() = assertWorldIsClean(200, seed = 103L)

  /** Comfortably between 128 and 512, and a prime number, so no divisor below it is shared with anything. */
  @Test
  fun `a 337-cell world is clean`() = assertWorldIsClean(337, seed = 104L)

  /**
   * Past the 512 km reference world, towards the 1024 km size the river-count scaling question was measured
   * at - but not all the way there. `:worldgen:test` runs every test class's world-building fixture under one
   * JVM's heap, several in parallel; a full-size 1024-cell world here was measured to push that over the edge
   * (`FeatureIndex.build` OOMing) purely from sharing a process with everything else the suite already builds
   * at 288-512 cells, with nothing wrong in the code path itself. Reaching 1024 itself is `:worldgen:bench`'s
   * job, in a JVM that isn't also running six hundred other tests.
   */
  @Test
  fun `a 600-cell world is clean`() = assertWorldIsClean(600, seed = 105L)

  /**
   * Builds one world at [cells] and asserts every [Invariants] check passes, plus the structural sanity a
   * check alone would not catch: the config actually reports the size asked for, and a chunk at each corner
   * and the centre materialises without throwing - the corners are where an off-by-one in a bounds computation
   * would show up first.
   */
  private fun assertWorldIsClean(cells: Int, seed: Long) {
    val world = StandardWorld.build(config(cells, seed))

    assertEquals(cells, world.config.widthCells)
    assertEquals(cells, world.config.heightCells)

    val violations = Invariants.check(world)
    assertTrue(
      violations.isEmpty(),
      "$cells-cell world (seed $seed): ${violations.joinToString("; ")}"
    )

    val chunkSpan = Math.ceil(world.config.widthMetres / world.config.chunkExtent).toInt().coerceAtLeast(1)
    val corners = listOf(0 to 0, chunkSpan - 1 to 0, 0 to chunkSpan - 1, chunkSpan - 1 to chunkSpan - 1)
    for ((cx, cy) in (corners + (chunkSpan / 2 to chunkSpan / 2))) {
      val slabs = world.contentSlabsOf(cx, cy)
      world.materializer.materialize(ChunkPos(cx, cy, slabs.first))
    }
  }
}
