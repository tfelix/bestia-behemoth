package net.bestia.zone.water

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.derived.VoxelEdit
import net.bestia.worldgen.voxel.BlockType
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WaterServiceTest {

  private val config = WorldConfig(seed = 1L, widthCells = 8, heightCells = 8)

  private val written = HashMap<ChunkPos, LongArray>()

  private val chunkService = mockk<ChunkService> {
    every { config } returns this@WaterServiceTest.config
    every { normalise(any()) } answers { firstArg() }
    val chunk = slot<ChunkPos>()
    val edits = slot<LongArray>()
    every { editFluid(capture(chunk), capture(edits)) } answers {
      written[chunk.captured] = edits.captured
      edits.captured.size
    }
  }

  private val service = WaterService(chunkService)

  @Test
  fun `a pour is the lower half of a sphere`() {
    // Mid-chunk, so the whole bowl lands in one chunk.
    service.requestPour(16, 16, 100, radius = 1)

    val filled = service.drainPours()

    // The centre plane is a plus of five voxels, and the voxel below the centre makes six.
    assertEquals(6, filled)
    val edits = written.values.single()
    assertTrue(edits.all { VoxelEdit.blockOf(it) == BlockType.WATER })

    val centre = ChunkCoords.localise(config, 16, 16, 100)!!
    val below = ChunkCoords.voxelIndex(config, centre.localX, centre.localY, centre.localZ - 1)
    val above = ChunkCoords.voxelIndex(config, centre.localX, centre.localY, centre.localZ + 1)
    assertTrue(edits.any { VoxelEdit.indexOf(it) == below })
    assertTrue(edits.none { VoxelEdit.indexOf(it) == above }, "nothing is poured above the centre")
  }

  @Test
  fun `a pour across a chunk border writes each chunk once, sorted`() {
    service.requestPour(31, 16, 100, radius = 2)

    service.drainPours()

    assertEquals(2, written.size)
    for (edits in written.values) {
      assertEquals(edits.sorted(), edits.toList())
    }
  }

  @Test
  fun `a pour past the cap is refused`() {
    assertFailsWith<IllegalArgumentException> {
      service.requestPour(0, 0, 0, radius = WaterService.MAX_POUR_RADIUS + 1)
    }
  }
}
