package net.bestia.zone.world.stream

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.voxel.CarveBrush
import net.bestia.zone.world.GeneratedWorlds
import net.bestia.zone.world.WorldService
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.bestia.zone.world.persistence.PersistedChunkEdit

/** Edits taken from one [ChunkService] and restored into a fresh one, as a restart does through the database. */
class ChunkServiceRestoreTest {

  private fun newService(): ChunkService {
    val worldService: WorldService = mockk {
      every { generated } returns world
      every { config } returns world.config
      every { isLoaded } returns true
    }
    return ChunkService(worldService, ChunkStreamConfig())
  }

  /** Solid rock: a few voxels under the world's highest ground. */
  private fun carveUnderThePeak(service: ChunkService): Set<ChunkPos> {
    val elevation = world.world.layers.require<FloatLayer>(LayerId.ELEVATION)
    val region = elevation.region
    var bestX = region.minX
    var bestY = region.minY
    for (cellY in region.minY..region.maxY) {
      for (cellX in region.minX..region.maxX) {
        if (elevation[cellX, cellY] > elevation[bestX, bestY]) {
          bestX = cellX
          bestY = cellY
        }
      }
    }

    val metresPerCell = world.config.baseResolution.metresPerCell
    val voxelX = (bestX * metresPerCell / world.config.voxelSize).toLong()
    val voxelY = (bestY * metresPerCell / world.config.voxelSize).toLong()
    val voxelZ = (service.surfaceElevationAt(voxelX, voxelY)!! / world.config.voxelSize).toLong() - DEPTH

    val result = service.carve(CarveBrush.sphere(voxelX + 0.5, voxelY + 0.5, voxelZ + 0.5, CARVE_RADIUS))
    assertTrue(result.voxels.isNotEmpty(), "nothing was carved under the peak at voxel z $voxelZ")

    return result.chunks
  }

  /** Through the row and back, so the test covers what the database actually stores. */
  private fun throughTheTable(saved: ChunkService.SavedEdit): ChunkService.SavedEdit {
    return PersistedChunkEdit.of(saved, worldShapeVersion = 1L, pipelineVersion = 2L).toSavedEdit()
  }

  @Test
  fun `a restored chunk reads, revises and is offered as it was before the restart`() {
    val before = newService()
    val carved = carveUnderThePeak(before)

    val saved = before.drainUnsaved()
    assertEquals(carved, saved.map { it.chunk }.toSet())

    val after = newService()
    saved.map(::throughTheTable).forEach(after::restore)

    for (chunk in carved) {
      assertContentEquals(before.merged(chunk).blocks, after.merged(chunk).blocks)
      assertContentEquals(before.merged(chunk).occupancy, after.merged(chunk).occupancy)
      assertEquals(before.revisionOf(chunk), after.revisionOf(chunk))
      assertContentEquals(before.surfaceSlabsOf(chunk), after.surfaceSlabsOf(chunk))
    }
    assertEquals(emptyList(), after.drainUnsaved(), "a restored chunk is already saved")
  }

  @Test
  fun `each edited chunk is handed over once until it changes again`() {
    val service = newService()
    carveUnderThePeak(service)

    assertTrue(service.drainUnsaved().isNotEmpty())
    assertEquals(emptyList(), service.drainUnsaved())
  }

  @Test
  fun `a listener that registers late still hears of every edited chunk`() {
    val before = newService()
    val carved = carveUnderThePeak(before)
    val after = newService()
    before.drainUnsaved().forEach(after::restore)

    val heard = HashSet<ChunkPos>()
    after.onChunkChanged { heard.add(it) }

    assertEquals(carved, heard)
  }

  private companion object {
    const val DEPTH = 6
    const val CARVE_RADIUS = CarveBrush.MIN_RADIUS + 0.4

    val world = GeneratedWorlds.of(seed = 9001L, cells = 48)
  }
}
