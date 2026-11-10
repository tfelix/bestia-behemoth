package net.bestia.zone.casting.sense

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.VoxelChunk
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.stream.ChunkService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HollowFinderTest {

  private val config = WorldConfig(seed = 1L)

  /** Ground up to z = 60 everywhere. Below it, rock, except the air cells this test lists. */
  private fun finderWith(air: (Int, Int, Int) -> Boolean): HollowFinder {
    // A chunk is read in its own local coordinates, so each one maps them back to world ones.
    fun chunkAt(pos: ChunkPos) = mockk<VoxelChunk> {
      every { this@mockk[any(), any(), any()] } answers {
        val x = pos.x * config.chunkSize + firstArg<Int>()
        val y = pos.y * config.chunkSize + secondArg<Int>()
        val z = pos.z * config.chunkHeight + thirdArg<Int>()
        if (air(x, y, z)) BlockType.AIR else BlockType.GRANITE
      }
    }
    val chunks = mockk<ChunkService> {
      every { config } returns this@HollowFinderTest.config
      every { surfaceElevationAt(any(), any()) } returns SURFACE.toDouble()
      every { normalise(any()) } answers { firstArg<ChunkPos>() }
      every { merged(any()) } answers { chunkAt(firstArg()) }
    }

    return HollowFinder(chunks)
  }

  @Test
  fun `a buried chamber is found with its depth and size`() {
    // A 4 x 3 x 2 room whose ceiling is 10 m under the surface.
    val sut = finderWith { x, y, z -> x in 10..13 && y in 10..12 && z in 49..50 }

    val hollow = sut.around(Vec3L(12, 12, 55), radius = 10).single()

    assertEquals(24, hollow.voxels)
    assertEquals(4, hollow.size)
    assertEquals(SURFACE - 50, hollow.depth)
  }

  @Test
  fun `a crack too small to matter, and the sky, are not hollows`() {
    val sut = finderWith { x, y, z -> (x == 5 && y == 5 && z == 45) || z >= SURFACE }

    assertTrue(sut.around(Vec3L(5, 5, 55), radius = 10).isEmpty())
  }

  @Test
  fun `a hollow beyond the radius is not heard`() {
    val sut = finderWith { x, y, z -> x in 25..28 && y in 0..3 && z in 49..50 }

    assertTrue(sut.around(Vec3L(0, 0, 55), radius = 10).isEmpty())
    assertEquals(1, sut.around(Vec3L(0, 0, 55), radius = 30).size)
  }

  private companion object {
    const val SURFACE = 60
  }
}
