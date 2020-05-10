package net.bestia.voxel.io

import net.bestia.voxel.Chunk
import net.bestia.voxel.Vector3
import net.bestia.voxel.Voxel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@ExperimentalUnsignedTypes
class ChunkRawSerializerTest {
  private val writer = ChunkCompressWriter()
  private val reader = ChunkCompressReader()

  @Test
  fun `serializing an chunk of empty voxel works`() {
    val writeChunk = Chunk.makeEmpy()
    writeChunk.fill(Voxel.of(1, 1.0f))
    val data = writer.write(writeChunk)

    val readChunk = reader.read(data)
    assertEquals(writeChunk, readChunk)
  }

  @Test
  fun `serializing an chunk with the lower 3 rows filled with mat`() {
    val writeChunk = Chunk.makeEmpy()
    val earthVoxel = Voxel.of(1, 1f)

    for (x in 0 until writeChunk.size) {
      for (y in 0 until writeChunk.size) {
        for (z in 0..3) {
          writeChunk.setVoxel(Vector3(x, y, z), earthVoxel)
        }
      }
    }

    val data = writer.write(writeChunk)

    val readChunk = reader.read(data)
    assertEquals(writeChunk, readChunk)
  }
}