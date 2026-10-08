package net.bestia.zone.world.stream

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.derived.VoxelEdit
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.zone.world.WorldService
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ChunkServiceFluidEditTest {

  private val service: ChunkService = ChunkService(
    mockk<WorldService> {
      every { generated } returns world
      every { config } returns world.config
      every { isLoaded } returns true
    },
    ChunkStreamConfig()
  )

  /** A dry column of the surface slab, with rock a few voxels down and air a few voxels up. */
  private class Land(val chunk: ChunkPos, val rockIndex: Int, val airIndex: Int)

  private fun findLand(): Land {
    val size = config.chunkSize
    val height = config.chunkHeight
    // The edges are forced ocean, so look outward from the centre.
    val centre = (config.widthMetres / config.chunkExtent).toInt() / 2

    for (step in 0 until 200) {
      val chunkX = centre + step * 3
      for (localX in 0 until size step 7) {
        val column = ChunkPos(chunkX, chunkX, 0)
        val elevation = world.columns.heights(column, 0)[localX, localX]
        if (elevation < DRY_ABOVE_SEA_METRES) continue

        val surfaceZ = config.voxelZOf(elevation)
        val localZ = Math.floorMod(surfaceZ, height)
        if (localZ < 4 || localZ > height - 5) continue

        val chunk = ChunkPos(chunkX, chunkX, Math.floorDiv(surfaceZ, height))
        val voxels = service.merged(chunk)
        val rock = voxels.index(localX, localX, localZ - 3)
        val air = voxels.index(localX, localX, localZ + 3)
        if (VoxelEdit.blockOf(VoxelEdit.of(voxels, rock)).solid && voxels.blocks[air] == BlockType.AIR.id.toByte()) {
          return Land(chunk, rock, air)
        }
      }
    }

    error("no dry land column found in the fixture world")
  }

  private fun water(index: Int, occupancy: Int = Occupancy.FULL): Long {
    return VoxelEdit.pack(index, BlockType.WATER, occupancy)
  }

  @Test
  fun `water fills air and is announced to holders`() {
    val land = findLand()

    val changed = service.editFluid(land.chunk, longArrayOf(water(land.airIndex, occupancy = 120)))

    assertEquals(1, changed)
    assertEquals(BlockType.WATER.id.toByte(), service.merged(land.chunk).blocks[land.airIndex])
    assertEquals(1, service.revisionOf(land.chunk))
    val change = service.drainChanges().single { it.chunk == land.chunk }
    assertContentEquals(longArrayOf(water(land.airIndex, occupancy = 120)), change.edits)
  }

  @Test
  fun `rock is never overwritten`() {
    val land = findLand()
    val before = service.merged(land.chunk).blocks[land.rockIndex]

    val changed = service.editFluid(land.chunk, longArrayOf(water(land.rockIndex)))

    assertEquals(0, changed)
    assertEquals(before, service.merged(land.chunk).blocks[land.rockIndex])
    assertEquals(0, service.revisionOf(land.chunk), "nothing changed, so nothing is announced")
  }

  @Test
  fun `only air and water may be written`() {
    val land = findLand()
    val granite = VoxelEdit.pack(land.airIndex, BlockType.GRANITE, Occupancy.FULL)

    assertEquals(0, service.editFluid(land.chunk, longArrayOf(granite)))
    assertEquals(BlockType.AIR.id.toByte(), service.merged(land.chunk).blocks[land.airIndex])
  }

  @Test
  fun `water that drains away leaves the generated voxel`() {
    val land = findLand()
    val air = VoxelEdit.pack(land.airIndex, BlockType.AIR, Occupancy.EMPTY)

    service.editFluid(land.chunk, longArrayOf(water(land.airIndex)))
    val changed = service.editFluid(land.chunk, longArrayOf(air))

    assertEquals(1, changed)
    assertEquals(BlockType.AIR.id.toByte(), service.merged(land.chunk).blocks[land.airIndex])
    assertEquals(2, service.revisionOf(land.chunk))
  }

  @Test
  fun `a flood does not make its chunk one whose walkability is kept`() {
    val land = findLand()

    service.editFluid(land.chunk, longArrayOf(water(land.airIndex)))
    service.derived().rebuildAll()

    assertFalse(service.derived().isTracked(land.chunk))
  }

  private companion object {
    const val SEED = 9001L
    const val WORLD_CELLS = 48
    const val DRY_ABOVE_SEA_METRES = 6.0

    // In the companion so JUnit's per-method instances share one generated world. Read-only here.
    val world = StandardWorld.build(WorldConfig(seed = SEED, widthCells = WORLD_CELLS, heightCells = WORLD_CELLS))
    val config = world.config
  }
}
