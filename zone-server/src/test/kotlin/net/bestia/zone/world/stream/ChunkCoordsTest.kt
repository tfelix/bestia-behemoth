package net.bestia.zone.world.stream

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.voxel.VoxelChunk
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ChunkCoordsTest {

  private val config = StandardWorld.demoConfig()

  @Test
  fun `a position maps to the chunk containing it`() {
    assertEquals(ChunkPos(0, 0, 0), ChunkCoords.chunkOf(config, Vec3L(0, 0, 0)))
    assertEquals(ChunkPos(0, 0, 0), ChunkCoords.chunkOf(config, Vec3L(31, 31, 255)))
    assertEquals(ChunkPos(1, 1, 1), ChunkCoords.chunkOf(config, Vec3L(32, 32, 256)))
  }

  @Test
  fun `negative positions floor rather than truncate`() {
    // Truncation towards zero would put -1 and 0 in the same chunk and leave chunk -1 one voxel short. Every
    // coordinate west or south of the origin, and every voxel below sea level, depends on this.
    assertEquals(ChunkPos(-1, -1, -1), ChunkCoords.chunkOf(config, Vec3L(-1, -1, -1)))
    assertEquals(ChunkPos(-1, -1, -1), ChunkCoords.chunkOf(config, Vec3L(-32, -32, -256)))
    assertEquals(ChunkPos(-2, -2, -2), ChunkCoords.chunkOf(config, Vec3L(-33, -33, -257)))
  }

  @Test
  fun `localising gives coordinates inside the chunk it names`() {
    val localised = assertNotNull(ChunkCoords.localise(config, -33, 70, -257))

    assertEquals(ChunkPos(-2, 2, -2), localised.chunk)
    assertEquals(31, localised.localX, "-33 is the last column of chunk -2, not the first of chunk -1")
    assertEquals(6, localised.localY)
    assertEquals(255, localised.localZ)
  }

  @Test
  fun `the voxel index matches the chunk layout it is written against`() {
    // The patch format carries this index and the client decodes with it, so a divergence from VoxelChunk's
    // own arithmetic would put every edit in the wrong place - and only in chunks where the mistake happens
    // to matter, which is the worst way for it to show up.
    val chunk = VoxelChunk(ChunkPos(0, 0, 0), config.chunkSize, config.chunkHeight)

    for ((x, y, z) in listOf(Triple(0, 0, 0), Triple(31, 31, 255), Triple(5, 17, 200))) {
      assertEquals(
        chunk.index(x, y, z),
        ChunkCoords.voxelIndex(config, x, y, z),
        "index disagreed at ($x,$y,$z)"
      )
    }
  }

  @Test
  fun `a chunk address survives the proto round trip including negatives`() {
    val chunk = ChunkPos(-123_456, 987_654, -3)

    assertEquals(chunk, ChunkCoords.fromProto(ChunkCoords.toProto(chunk)))
  }
}
