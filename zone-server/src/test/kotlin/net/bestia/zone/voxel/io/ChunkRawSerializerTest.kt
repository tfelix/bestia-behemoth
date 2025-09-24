package net.bestia.zone.voxel.io

import net.bestia.zone.geometry.Vec3
import net.bestia.zone.geometry.Vec3I
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.voxel.Chunk
import net.bestia.zone.voxel.Voxel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@ExperimentalUnsignedTypes
class ChunkRawSerializerTest {
  private val writer = ChunkCompressWriter()
  private val reader = ChunkCompressReader()

  @Test
  fun `serializing an chunk of empty voxel works`() {
    val writeChunk = Chunk.makeEmpty()
    writeChunk.fill(Voxel.of(1, 1.0f))
    val data = writer.write(writeChunk)

    val readChunk = reader.read(data)
    assertEquals(writeChunk, readChunk)
  }

  @Test
  fun `serializing an chunk with the lower 3 rows filled with mat`() {
    val writeChunk = Chunk.makeEmpty()
    val earthVoxel = Voxel.of(1, 1f)

    for (x in 0 until writeChunk.size) {
      for (y in 0 until writeChunk.size) {
        for (z in 0..3) {
          writeChunk.setVoxel(Vec3I(x, y, z), earthVoxel)
        }
      }
    }

    val data = writer.write(writeChunk)

    val readChunk = reader.read(data)
    assertEquals(writeChunk, readChunk)
  }
}