package net.bestia.zone.world.stream

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.voxel.CarveBrush
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression for the slab-zero pin: every query in [ChunkWalkQuery] localised at `voxelZ = 0`, so it read the
 * slab covering sea level to 256 m whatever the caller's height, and then handed that slab's *chunk-local*
 * surface height back as though it were an elevation.
 *
 * The two mistakes cancel exactly once - for slab zero - which is why ground between 0 and 256 m worked and
 * nothing above it did. On a mountain the query read bedrock with no clearance, found no standable span, and
 * returned `null`. `MoveSystem` then fell through to whatever height the client claimed, so the server never
 * corrected an entity's z at all: a player could not descend into a shaft they had dug, and every one of these
 * assertions would have failed.
 *
 * Against a genuinely generated world rather than a synthetic grid, for the reason the other `ChunkService`
 * regressions here are: what broke was real terrain heights meeting real chunk arithmetic, and a hand-built
 * chunk at `z = 0` is precisely the one case the old code got right.
 */
class ChunkWalkQueryTest {

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

  /** A voxel column and the heightfield's own elevation for it, chosen to be well above one slab of ground. */
  private class HighColumn(val voxelX: Long, val voxelY: Long, val elevation: Double)

  /**
   * The highest voxel column in the chunk under the world's highest kilometre cell.
   *
   * Off the `ELEVATION` raster first, because a 48 km world is 1 500 chunks across and one `heights()` call is
   * a feature query plus a thousand noise evaluations - then one such call to pick the column inside it.
   */
  private val high: HighColumn by lazy {
    val elevation = world.world.layers.require<FloatLayer>(LayerId.ELEVATION)
    val region = elevation.region

    var peakX = region.minX
    var peakY = region.minY
    var peak = Float.NEGATIVE_INFINITY

    for (cellY in region.minY..region.maxY) {
      for (cellX in region.minX..region.maxX) {
        val height = elevation[cellX, cellY]
        if (height > peak) {
          peak = height
          peakX = cellX
          peakY = cellY
        }
      }
    }

    val metresPerCell = config.baseResolution.metresPerCell
    val chunkX = chunkOfMetres(peakX * metresPerCell)
    val chunkY = chunkOfMetres(peakY * metresPerCell)

    val heights = world.columns.heights(ChunkPos(chunkX, chunkY, 0), 0)
    var bestLocalX = 0
    var bestLocalY = 0
    var best = -Double.MAX_VALUE

    for (localY in 0 until config.chunkSize) {
      for (localX in 0 until config.chunkSize) {
        val height = heights[localX, localY]
        if (height > best) {
          best = height
          bestLocalX = localX
          bestLocalY = localY
        }
      }
    }

    HighColumn(
      voxelX = chunkX.toLong() * config.chunkSize + bestLocalX,
      voxelY = chunkY.toLong() * config.chunkSize + bestLocalY,
      elevation = best
    )
  }

  private fun chunkOfMetres(metres: Double): Int =
    Math.floorDiv((metres / config.voxelSize).toLong(), config.chunkSize.toLong()).toInt()

  private fun chunkOf(voxelX: Long, voxelY: Long, voxelZ: Long) = ChunkPos(
    Math.floorDiv(voxelX, config.chunkSize.toLong()).toInt(),
    Math.floorDiv(voxelY, config.chunkSize.toLong()).toInt(),
    Math.floorDiv(voxelZ, config.chunkHeight.toLong()).toInt()
  )

  /** Gives a chunk its derived structures, the way a subscription does at runtime. */
  private fun track(service: ChunkService, vararg chunks: ChunkPos) {
    chunks.forEach { service.derived().track(it) }
    service.derived().rebuildAll()
  }

  private fun standingZ() = ChunkCoords.standingZ(config, high.elevation)

  @Test
  fun `the surface of a column high above sea level is found where the heightfield says it is`() {
    val service = newService()
    val query = ChunkWalkQuery(service)

    val standing = standingZ()
    val slab = chunkOf(high.voxelX, high.voxelY, standing)

    assertTrue(
      slab.z > 0,
      "this test needs ground above one whole slab, or it would pass against the slab-zero pin too - the " +
          "highest column in seed $SEED is at ${high.elevation} m, in slab ${slab.z}"
    )

    val position = Vec3L(high.voxelX, high.voxelY, standing)

    // Nothing is tracked yet, and that is the documented contract of `isTracked`: a pathfinder must be able to
    // ask what is cheap and treat the rest as unknown rather than materialise half a megabyte of voxels.
    assertNull(query.surfaceAt(position), "an untracked chunk has no answer to give")

    track(service, slab)

    val answer = assertNotNull(
      query.surfaceAt(position),
      "the column's own slab is tracked and holds standable ground, so there is an answer"
    )

    assertTrue(
      Math.abs(answer - standing) <= TOLERANCE_VOXELS,
      "the walkable tile put the surface at $answer, the heightfield at $standing (${high.elevation} m) - a " +
          "gap of ${answer - standing} voxels is the slab base going missing, not rounding"
    )
  }

  @Test
  fun `a position in the slab above its own ground still finds that ground`() {
    val service = newService()
    val query = ChunkWalkQuery(service)

    val standing = standingZ()
    val ground = chunkOf(high.voxelX, high.voxelY, standing)
    val above = ground.copy(z = ground.z + 1)

    // The floor of the slab above the ground: nothing but air in this column, so the answer can only come from
    // the slab below. Both are tracked, so the fallback is driven by there genuinely being no surface up here
    // rather than by the upper slab being unknown.
    track(service, ground, above)

    val position = Vec3L(high.voxelX, high.voxelY, above.z.toLong() * config.chunkHeight)

    val answer = assertNotNull(
      query.surfaceAt(position),
      "a shaft floor a voxel under a slab boundary belongs to the slab beneath whoever stands on it, and this " +
          "is the same lookup"
    )

    assertTrue(
      Math.abs(answer - standing) <= TOLERANCE_VOXELS,
      "the ground below is at $standing; the query answered $answer"
    )
  }

  @Test
  fun `carving the top off a column moves the surface down, because the tiles read merged voxels`() {
    val service = newService()
    val query = ChunkWalkQuery(service)

    val standing = standingZ()
    val slab = chunkOf(high.voxelX, high.voxelY, standing)
    track(service, slab)

    val position = Vec3L(high.voxelX, high.voxelY, standing)
    val before = assertNotNull(query.surfaceAt(position))

    // Centred just under the surface so the brush opens the column rather than leaving a sealed gallery under
    // intact ground - a gallery would leave the original surface standing, which it should.
    val carved = service.carve(
      CarveBrush.sphere(
        high.voxelX + 0.5,
        high.voxelY + 0.5,
        (standing - 1).toDouble() + 0.5,
        CarveBrush.MIN_RADIUS + 0.4
      )
    )
    assertTrue(carved.voxels.isNotEmpty(), "nothing was carved just below the surface at ${high.elevation} m")

    // `ChunkService.carve` marks the tile stale; the rebuild is budgeted, so a test has to pay for it.
    service.derived().rebuildAll()

    val after = assertNotNull(query.surfaceAt(position), "a carved column still has a floor to stand on")

    assertTrue(
      after < before,
      "the tiles are built from merged voxels, so digging the top off a column has to lower its reported " +
          "surface: $before -> $after"
    )
  }

  @Test
  fun `residency is answered for the slab the position is in, not for slab zero`() {
    val service = newService()
    val query = ChunkWalkQuery(service)

    val standing = standingZ()
    val slab = chunkOf(high.voxelX, high.voxelY, standing)
    val position = Vec3L(high.voxelX, high.voxelY, standing)

    assertTrue(!query.isResident(position), "nothing is tracked yet")

    // Deliberately the *wrong* slab first: the column's slab-zero chunk, which is what the old code would have
    // consulted. Tracking it must not make a position 1 000 m up resident.
    track(service, ChunkPos(slab.x, slab.y, 0))
    assertTrue(!query.isResident(position), "slab zero is a kilometre below this position and says nothing")

    track(service, slab)
    assertTrue(query.isResident(position), "the slab holding the position is tracked")
  }

  private companion object {
    const val SEED = 9001L
    const val WORLD_CELLS = 48

    /**
     * Half a voxel is as close as an integer `z` can get to a sub-voxel surface, and the walkable tile rounds
     * a partially filled floor voxel conservatively - so one voxel of slack. The bug this guards against was
     * off by whole slabs.
     */
    const val TOLERANCE_VOXELS = 1L
  }
}
