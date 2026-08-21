package net.bestia.zone.world.stream

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.voxel.CarveBrush
import net.bestia.zone.world.WorldService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression for the carve-near-a-border seam: the client's `TerrainPatch.Gather` reads a neighbour's edge
 * voxels into its own mesh (the mesher's apron), so a chunk whose own voxels never changed can still need to
 * re-mesh when a carve lands close enough to a shared border. `ChunkService.carve` has to tell a holder about
 * that neighbour too, with an empty patch, or the two sides of the border draw from different data forever.
 *
 * **On all three axes.** The vertical arm is the later half of the same bug: a chunk draws the surface at its
 * own *floor* and `GatherStrip` crosses into the slab below to do it, so a carve near a slab boundary leaves
 * the neighbouring slab's mesh stale exactly where a shaft crosses it. `TerrainRenderer.Invalidate` only
 * revisits chunks that recorded this one as *missing*, and a slab that is held is not in that list, so nothing
 * else would ever repair it.
 *
 * Against a genuinely generated world, for the same reason [ChunkServiceCaveStreamingTest] is: a synthetic
 * voxel grid would only prove the touch bookkeeping in isolation, not that a real carve's `voxelX`/`voxelY`
 * localise the way this depends on.
 */
class ChunkServiceApronTouchTest {

  private val world = StandardWorld.build(WorldConfig(seed = SEED, widthCells = WORLD_CELLS, heightCells = WORLD_CELLS))
  private val config = world.config

  private fun newService(): ChunkService {
    val worldService: WorldService = mockk {
      every { generated } returns world
      every { config } returns world.config
      every { isLoaded } returns true
    }
    return ChunkService(worldService, ChunkStreamConfig())
  }

  /** A global voxel z comfortably below the generated surface at ([voxelX],[voxelY]), so a carve always finds rock. */
  private fun solidVoxelZAt(voxelX: Long, voxelY: Long): Int {
    val size = config.chunkSize.toLong()
    val chunk = ChunkPos(Math.floorDiv(voxelX, size).toInt(), Math.floorDiv(voxelY, size).toInt(), 0)
    val localX = Math.floorMod(voxelX, size).toInt()
    val localY = Math.floorMod(voxelY, size).toInt()

    val elevation = world.columns.heights(chunk, 0)[localX, localY]
    return config.voxelZOf(elevation - SAFETY_MARGIN_METRES)
  }

  /**
   * Carves a small sphere centred on ([voxelX],[voxelY]) at a depth guaranteed solid, and returns the chunk it
   * landed in.
   *
   * The z chunk is derived from the real generated terrain height, not assumed to be zero - a voxel z of zero
   * is sea level, not the terrain surface, and this world's ground can sit at any z chunk depending on where
   * ([voxelX],[voxelY]) happens to fall.
   *
   * [CarveBrush.MIN_RADIUS] plus a small margin, not exactly the minimum: big enough that the voxels this
   * test cares about are well inside the sphere rather than on its fringe, so which corners the supersampler
   * happens to count is not what the assertions are resting on.
   */
  private fun carveAt(service: ChunkService, voxelX: Long, voxelY: Long): ChunkPos {
    val size = config.chunkSize.toLong()
    val voxelZ = solidVoxelZAt(voxelX, voxelY)

    service.carve(CarveBrush.sphere(voxelX + 0.5, voxelY + 0.5, voxelZ + 0.5, CARVE_RADIUS))

    return ChunkPos(
      Math.floorDiv(voxelX, size).toInt(),
      Math.floorDiv(voxelY, size).toInt(),
      Math.floorDiv(voxelZ.toLong(), config.chunkHeight.toLong()).toInt()
    )
  }

  @Test
  fun `a carve near a chunk's high-x edge also touches the +x neighbour, and no others`() {
    val service = newService()
    val size = config.chunkSize.toLong()

    // Three voxels off the high-x edge: within CARVE_RADIUS of the last two columns (TerrainPatch.ApronLow),
    // but its true geometric minimum distance to any real voxel of the (1,0) neighbour exceeds CARVE_RADIUS,
    // so that neighbour's own content must not change.
    val origin = carveAt(service, voxelX = size - 3, voxelY = size / 2)

    val changes = service.drainChanges().associateBy { it.chunk }

    val real = changes[origin]
    assertNotNull(real, "the carved chunk itself must be reported")
    assertTrue(real!!.removals.isNotEmpty(), "the carved chunk's own voxels changed")

    val neighbour = changes[origin.copy(x = origin.x + 1)]
    assertNotNull(neighbour, "the +x neighbour reads this chunk's high edge into its mesh apron and must be told")
    assertTrue(neighbour!!.removals.isEmpty(), "the neighbour's own content did not change")
    assertEquals(neighbour.fromRevision, neighbour.toRevision, "an apron-only touch must not bump a revision")

    for (dx in -1..1) {
      for (dy in -1..1) {
        if (dx == 0 && dy == 0) continue
        if (dx == 1 && dy == 0) continue
        assertNull(
          changes[origin.copy(x = origin.x + dx, y = origin.y + dy)],
          "chunk offset ($dx,$dy) has no border with the carve and must not be touched"
        )
      }
    }
  }

  @Test
  fun `a carve well inside a chunk touches no neighbour at all`() {
    val service = newService()
    val size = config.chunkSize.toLong()

    val origin = carveAt(service, voxelX = size / 2, voxelY = size / 2)

    val changes = service.drainChanges().associateBy { it.chunk }
    assertNotNull(changes[origin], "the carved chunk itself must be reported")

    for (dx in -1..1) {
      for (dy in -1..1) {
        if (dx == 0 && dy == 0) continue
        assertNull(
          changes[origin.copy(x = origin.x + dx, y = origin.y + dy)],
          "an interior carve must not touch chunk offset ($dx,$dy)"
        )
      }
    }
  }

  @Test
  fun `a carve near a chunk corner touches exactly the two edge neighbours and the diagonal, not all eight`() {
    val service = newService()
    val size = config.chunkSize.toLong()

    val origin = carveAt(service, voxelX = size - 3, voxelY = size - 3)

    val changes = service.drainChanges().associateBy { it.chunk }

    assertNotNull(changes[origin], "the carved chunk itself")
    assertNotNull(changes[origin.copy(x = origin.x + 1)], "the +x edge neighbour")
    assertNotNull(changes[origin.copy(y = origin.y + 1)], "the +y edge neighbour")

    val diagonal = changes[origin.copy(x = origin.x + 1, y = origin.y + 1)]
    assertNotNull(diagonal, "the diagonal neighbour sharing the corner")
    assertTrue(diagonal!!.removals.isEmpty(), "the diagonal neighbour's own content did not change")

    val untouchedOffsets = listOf(-1 to 0, 0 to -1, -1 to -1, -1 to 1, 1 to -1)
    for ((dx, dy) in untouchedOffsets) {
      assertNull(
        changes[origin.copy(x = origin.x + dx, y = origin.y + dy)],
        "chunk offset ($dx,$dy) does not border the carve and must not be touched"
      )
    }
  }

  /**
   * Carves at an explicit local z inside a slab that is guaranteed solid, and returns the chunk it landed in.
   *
   * A whole slab below the one holding [solidVoxelZAt] rather than that slab itself, so every local z from the
   * floor to the ceiling is buried: the tests below need to aim at a slab *ceiling*, and the ceiling of the
   * slab the surface is in would be open air.
   */
  private fun carveAtLocalZ(service: ChunkService, localZ: Int): ChunkPos {
    val size = config.chunkSize.toLong()
    val voxelX = size / 2
    val voxelY = size / 2

    val surfaceSlab = Math.floorDiv(solidVoxelZAt(voxelX, voxelY).toLong(), config.chunkHeight.toLong()).toInt()
    val slab = surfaceSlab - 1
    val voxelZ = slab * config.chunkHeight + localZ

    service.carve(CarveBrush.sphere(voxelX + 0.5, voxelY + 0.5, voxelZ + 0.5, CARVE_RADIUS))

    return ChunkPos(
      Math.floorDiv(voxelX, size).toInt(),
      Math.floorDiv(voxelY, size).toInt(),
      slab
    )
  }

  @Test
  fun `a carve near a slab ceiling also touches the slab above, and no others`() {
    val service = newService()

    // Three voxels off the ceiling, exactly mirroring the horizontal case above: within CARVE_RADIUS of the
    // top two cells (TerrainPatch.ApronLow), while the nearest voxel of the slab above stays 2.5 voxels away
    // and so must not change.
    val origin = carveAtLocalZ(service, config.chunkHeight - 3)

    val changes = service.drainChanges().associateBy { it.chunk }

    val real = changes[origin]
    assertNotNull(real, "the carved chunk itself must be reported")
    assertTrue(real!!.removals.isNotEmpty(), "the carved chunk's own voxels changed")

    val above = changes[origin.copy(z = origin.z + 1)]
    assertNotNull(above, "the slab above reads this chunk's top cells into its mesh apron and must be told")
    assertTrue(above!!.removals.isEmpty(), "the slab above's own content did not change")
    assertEquals(above.fromRevision, above.toRevision, "an apron-only touch must not bump a revision")

    assertNull(
      changes[origin.copy(z = origin.z - 1)],
      "the slab below shares no boundary with a carve at the ceiling and must not be touched"
    )
  }

  @Test
  fun `a carve near a slab floor also touches the slab below, and no others`() {
    val service = newService()

    val origin = carveAtLocalZ(service, 2)

    val changes = service.drainChanges().associateBy { it.chunk }
    assertNotNull(changes[origin], "the carved chunk itself must be reported")

    val below = changes[origin.copy(z = origin.z - 1)]
    assertNotNull(below, "a chunk draws the surface at its own floor from the slab beneath, which must be told")
    assertTrue(below!!.removals.isEmpty(), "the slab below's own content did not change")

    assertNull(
      changes[origin.copy(z = origin.z + 1)],
      "the slab above shares no boundary with a carve at the floor and must not be touched"
    )
  }

  @Test
  fun `a carve in mid-slab touches neither vertical neighbour`() {
    val service = newService()

    val origin = carveAtLocalZ(service, config.chunkHeight / 2)

    val changes = service.drainChanges().associateBy { it.chunk }
    assertNotNull(changes[origin], "the carved chunk itself must be reported")

    for (dz in listOf(-1, 1)) {
      assertNull(
        changes[origin.copy(z = origin.z + dz)],
        "slab offset $dz has no border with a mid-slab carve and must not be touched"
      )
    }
  }

  private companion object {
    const val SEED = 9001L
    const val WORLD_CELLS = 48
    const val SAFETY_MARGIN_METRES = 10.0
    const val CARVE_RADIUS = CarveBrush.MIN_RADIUS + 0.4
  }
}
