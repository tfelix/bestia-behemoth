package net.bestia.zone.world.stream

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.voxel.CarveBrush
import net.bestia.zone.world.WorldService
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Regression for the carved-shaft streaming gap: a slab that holds something only because somebody dug into it
 * must still be offered by [ChunkService.surfaceSlabsOf].
 *
 * The heightfield cannot express a hole, and the offered slab set used to be derived from nothing else - so a
 * shaft carved through the floor of the lowest slab a column's ground reaches landed in a slab that was
 * generated, cached, and never announced. The client cannot compensate: `TerrainPatch.GatherStrip` extends the
 * lowest voxel it actually holds downward rather than reading absent terrain as air, and in a carved column
 * that voxel *is* air - so the floor of the shaft has no sign change to mesh and draws as nothing at all.
 *
 * Against a genuinely generated world, for the reason [ChunkServiceCaveStreamingTest] and
 * [ChunkServiceApronTouchTest] both are: the bug is real terrain heights disagreeing with real carve
 * coordinates, and a synthetic voxel grid would only prove the union logic in isolation.
 */
class ChunkServiceCarvedSlabStreamingTest {

  private val world =
    StandardWorld.build(WorldConfig(seed = SEED, widthCells = WORLD_CELLS, heightCells = WORLD_CELLS))

  private val config = world.config

  private fun newService(): ChunkService {
    val worldService: WorldService = mockk {
      every { generated } returns world
      every { config } returns world.config
      every { isLoaded } returns true
    }
    return ChunkService(worldService, ChunkStreamConfig())
  }

  /**
   * The horizontal chunk under the world's highest kilometre cell.
   *
   * Chosen off the `ELEVATION` raster rather than by scanning chunk column heights: a 48 km world is 1 500
   * chunks across and each `heights()` call is a feature query plus a thousand noise evaluations, while the
   * raster is 48x48 and already holds the answer to "where is this world's highest ground".
   *
   * Height is this test's whole precondition. The slab beneath the terrain span has to be one the generated
   * set genuinely does not mention, and slabs -1 and 0 are always offered because sea level lives there.
   */
  private val peak: ChunkPos by lazy {
    val elevation = world.world.layers.require<FloatLayer>(LayerId.ELEVATION)
    val region = elevation.region

    var bestX = region.minX
    var bestY = region.minY
    var best = Float.NEGATIVE_INFINITY

    for (cellY in region.minY..region.maxY) {
      for (cellX in region.minX..region.maxX) {
        val height = elevation[cellX, cellY]
        if (height > best) {
          best = height
          bestX = cellX
          bestY = cellY
        }
      }
    }

    val metresPerCell = config.baseResolution.metresPerCell
    ChunkPos(chunkOfMetres(bestX * metresPerCell), chunkOfMetres(bestY * metresPerCell), 0)
  }

  private fun chunkOfMetres(metres: Double): Int =
    Math.floorDiv((metres / config.voxelSize).toLong(), config.chunkSize.toLong()).toInt()

  /**
   * The lowest slab this column's generated terrain reaches, mirroring the terrain half of
   * `ChunkService.computeSurfaceSlabs`.
   *
   * Chunk-wide, like the real thing - so every column in the chunk has solid ground at and below this slab's
   * floor, which is what makes a carve down there certain to find rock.
   */
  private fun generatedBottomSlabOf(column: ChunkPos): Int {
    val heights = world.columns.heights(ChunkPos(column.x, column.y, 0), 0)

    var lowest = Double.MAX_VALUE
    for (localY in 0 until config.chunkSize) {
      for (localX in 0 until config.chunkSize) {
        val height = heights[localX, localY]
        if (height < lowest) lowest = height
      }
    }

    return config.chunkZOf(lowest)
  }

  private fun carveAt(service: ChunkService, column: ChunkPos, voxelZ: Int): ChunkService.CarveResult {
    val voxelX = column.x.toLong() * config.chunkSize + config.chunkSize / 2
    val voxelY = column.y.toLong() * config.chunkSize + config.chunkSize / 2

    return service.carve(CarveBrush.sphere(voxelX + 0.5, voxelY + 0.5, voxelZ + 0.5, CARVE_RADIUS))
  }

  @Test
  fun `a shaft carved through the floor of the generated span is still offered`() {
    val service = newService()
    val bottom = generatedBottomSlabOf(peak)
    val carved = bottom - 1

    val before = service.surfaceSlabsOf(peak).toSet()
    assertTrue(
      carved !in before,
      "this test needs a column whose generated slabs stop above slab $carved, or it would pass against the " +
          "old code too - seed $SEED bottoms out at slab $bottom and offers $before; pick another seed or a " +
          "larger world"
    )

    val voxelZ = bottom * config.chunkHeight - 1
    val result = carveAt(service, peak, voxelZ)
    assertTrue(
      result.voxels.isNotEmpty(),
      "nothing was carved at voxel z $voxelZ, which should be solid rock under the world's highest ground"
    )

    val after = service.surfaceSlabsOf(peak).toSet()

    assertTrue(carved in after, "the shaft reached slab $carved, which must now be offered (got $after)")
    assertTrue(
      carved - 1 in after,
      "slab ${carved - 1} is what the shaft's own floor draws its surface against, and offeredSlabs pulls in " +
          "a floor only when it is itself in the set (got $after)"
    )
  }

  @Test
  fun `a shaft that stops above a slab floor still offers the slab beneath it`() {
    val service = newService()
    val bottom = generatedBottomSlabOf(peak)

    assertTrue(
      bottom - 1 !in service.surfaceSlabsOf(peak).toSet(),
      "precondition: slab ${bottom - 1} must not already be offered for seed $SEED"
    )

    // Clear of the slab floor by more than the brush radius, so the carve genuinely stays inside `bottom` and
    // the slab below is offered because an edit was *recorded* against it, not because one landed in it.
    val result = carveAt(service, peak, bottom * config.chunkHeight + FLOOR_CLEARANCE)
    assertTrue(result.voxels.isNotEmpty(), "nothing was carved just above the floor of slab $bottom")
    assertTrue(
      result.chunks.all { it.z == bottom },
      "the brush was meant to stay inside slab $bottom but reached ${result.chunks.map { it.z }.distinct()}"
    )

    assertTrue(
      bottom - 1 in service.surfaceSlabsOf(peak).toSet(),
      "a shaft bottoming out on the floor of slab $bottom draws that floor from the slab beneath, so " +
          "${bottom - 1} has to be offered"
    )
  }

  @Test
  fun `the budgeted and unbudgeted slab lookups agree once a column has been dug in`() {
    val service = newService()
    val bottom = generatedBottomSlabOf(peak)

    service.surfaceSlabsOf(peak)
    carveAt(service, peak, bottom * config.chunkHeight - 1)

    // `ChunkStreamSystem.desiredChunks` takes whichever of these two the tick can afford. A narrower answer
    // from the cached one would offer terrain and withdraw it again depending on the slab budget.
    assertContentEquals(
      service.surfaceSlabsOf(peak),
      service.cachedSlabsOf(peak),
      "cachedSlabsOf must union the edits exactly as surfaceSlabsOf does"
    )
  }

  @Test
  fun `an untouched column allocates nothing and is resampled by neither call`() {
    val service = newService()

    val first = service.surfaceSlabsOf(peak)
    val computations = service.slabComputations

    // The same array, not merely equal contents: the union has to stay off the hot path for the overwhelming
    // majority of columns, which nobody has ever dug in. And asking again must not resample the heightfield,
    // which is the property `ChunkStreamingScenario` pins across a whole view volume.
    assertSame(first, service.surfaceSlabsOf(peak))
    assertSame(first, service.cachedSlabsOf(peak))
    assertTrue(
      service.slabComputations == computations,
      "asking again resampled the heightfield: $computations -> ${service.slabComputations}"
    )
  }

  private companion object {
    const val SEED = 9001L
    const val WORLD_CELLS = 48

    /** [CarveBrush.MIN_RADIUS] plus a margin, as in [ChunkServiceApronTouchTest] and for the same reason. */
    const val CARVE_RADIUS = CarveBrush.MIN_RADIUS + 0.4

    /** Voxels above a slab floor to carve at when the brush must *not* cross it. Exceeds [CARVE_RADIUS]. */
    const val FLOOR_CLEARANCE = 5
  }
}
