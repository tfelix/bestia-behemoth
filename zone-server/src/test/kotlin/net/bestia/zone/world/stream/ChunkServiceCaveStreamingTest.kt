package net.bestia.zone.world.stream

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.fail

/**
 * Regression for the cave-streaming gap: a cave passage below the terrain surface must be offered by
 * [ChunkService.surfaceSlabsOf], not silently dropped because only the terrain heightfield span was unioned.
 *
 * Against a genuinely generated world rather than a stub, because the bug is specifically that
 * `computeSurfaceSlabs` disagreed with `GeneratedWorld.contentSlabsOf` about which slabs a *real* cave
 * passage occupies - a synthetic feature list would only prove the union logic, not that the real
 * passage-emitting stage produces something this test can catch. A seed scan finds a world with a passage
 * whose column actually reaches past the terrain span (a shallow passage already inside it would pass
 * against the old, buggy code too, so it would not be a real regression test).
 */
class ChunkServiceCaveStreamingTest {

  @Test
  fun `a cave passage below the surface slab is still offered`() {
    val (world, column) = findColumnWithACaveBeyondTerrain()
      ?: fail(
        "no seed in the scanned range rolled a cave passage whose column reaches past the terrain-only " +
            "slab span; widen the scan"
      )

    val worldService: WorldService = mockk {
      every { generated } returns world
      every { config } returns world.config
      every { isLoaded } returns true
    }

    val chunkService = ChunkService(worldService, ChunkStreamConfig())
    val offered = chunkService.surfaceSlabsOf(ChunkPos(column.x, column.y, 0)).toSet()

    val expected = world.contentSlabsOf(column.x, column.y)
    for (z in expected) {
      assertTrue(
        z in offered,
        "slab $z of the cave passage at column $column was generated (contentSlabsOf: $expected) " +
            "but never offered by surfaceSlabsOf (got $offered)"
      )
    }
  }

  /**
   * Scans a handful of seeds for one holding a `CAVE_PASSAGE` whose column's `contentSlabsOf` reaches
   * further than the terrain heightfield alone would - i.e. a passage genuinely below (or above) the
   * surface slab, which is the one case the fixed union has to catch and the old code silently dropped.
   *
   * Checks only the columns under actual passage markers (typically a few dozen to a few hundred per
   * world, per `FeatureIndex`'s own sizing note), never a blind grid scan of every column - `heights()` is
   * cheap once per sampled point, not once per column in a world that can be thousands of chunks across.
   *
   * Sampled at a few points along each passage's own centerline (not the bounding box centre, which can
   * fall off a winding passage's actual path entirely) - a passage is a [net.bestia.worldgen.vector.Polyline],
   * so `pointAt` at fractions of its arc length is always a point genuinely on it.
   */
  private fun findColumnWithACaveBeyondTerrain(): Pair<GeneratedWorld, ChunkPos>? {
    for (seed in 1L..SEED_SCAN_LIMIT) {
      val world = StandardWorld.build(WorldConfig(seed = seed, widthCells = WORLD_CELLS, heightCells = WORLD_CELLS))
      val config = world.config

      val passages = world.world.features.all().filter { it.kind == FeatureKind.CAVE_PASSAGE }

      for (passage in passages) {
        val centerline = (passage as? MarkerFeature)?.centerline ?: continue

        for (fraction in SAMPLE_FRACTIONS) {
          val point = centerline.pointAt(centerline.length * fraction)
          val chunkX = Math.floorDiv((point.x / config.voxelSize).toLong(), config.chunkSize.toLong()).toInt()
          val chunkY = Math.floorDiv((point.y / config.voxelSize).toLong(), config.chunkSize.toLong()).toInt()

          val terrainOnly = terrainOnlySlabRange(world, chunkX, chunkY)
          val withCaves = world.contentSlabsOf(chunkX, chunkY)

          if (withCaves.first < terrainOnly.first || withCaves.last > terrainOnly.last) {
            return world to ChunkPos(chunkX, chunkY, 0)
          }
        }
      }
    }

    return null
  }

  /** Mirrors `GeneratedWorld.contentSlabsOf`'s own terrain-height half, without the passage union. */
  private fun terrainOnlySlabRange(world: GeneratedWorld, chunkX: Int, chunkY: Int): IntRange {
    val config = world.config
    val heights = world.columns.heights(ChunkPos(chunkX, chunkY, 0), 0)

    var lowest = Double.MAX_VALUE
    var highest = -Double.MAX_VALUE
    for (localY in 0 until config.chunkSize) {
      for (localX in 0 until config.chunkSize) {
        val h = heights[localX, localY]
        if (h < lowest) lowest = h
        if (h > highest) highest = h
      }
    }

    return config.chunkZOf(lowest - config.voxelSize)..config.chunkZOf(highest)
  }

  private companion object {
    const val SEED_SCAN_LIMIT = 15L
    const val WORLD_CELLS = 128
    val SAMPLE_FRACTIONS = listOf(0.0, 0.25, 0.5, 0.75, 1.0)
  }
}
