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
    val land = DryLand.find(world, service)
    val rock = ChunkCoords.localise(config, land.voxelX, land.voxelY, land.surfaceZ - 3L)!!
    val air = ChunkCoords.localise(config, land.voxelX, land.voxelY, land.surfaceZ + 3L)!!

    return Land(
      rock.chunk,
      ChunkCoords.voxelIndex(config, rock.localX, rock.localY, rock.localZ),
      ChunkCoords.voxelIndex(config, air.localX, air.localY, air.localZ)
    )
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

    // In the companion so JUnit's per-method instances share one generated world. Read-only here.
    val world = StandardWorld.build(WorldConfig(seed = SEED, widthCells = WORLD_CELLS, heightCells = WORLD_CELLS))
    val config = world.config
  }
}
