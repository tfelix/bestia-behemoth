package net.bestia.voxel

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ChunkTest {

  @Test
  fun `setVoxel and getVoxel return the right value`() {
    val c = Chunk.makeEmpy()
    val v1 = Voxel.of(1, 0.5f)
    val v2 = Voxel.of(2, 1f)
    val v3 = Voxel.of(0, 0f)

    val p1 = Vector3(6, 7, 9)
    val p2 = Vector3(7, 7, 9)
    val p3 = Vector3(8, 7, 9)

    c.setVoxel(p1, v1)
    c.setVoxel(p2, v2)
    c.setVoxel(p3, v3)

    Assertions.assertEquals(v1, c.getVoxel(p1))
    Assertions.assertEquals(v2, c.getVoxel(p2))
    Assertions.assertEquals(v3, c.getVoxel(p3))
  }

  @Test
  fun `fill() fills up the entire chunk with voxel`() {
    val c = Chunk.makeEmpy()
    val v = Voxel.of(1, 0.5f)
    c.fill(v)

    Assertions.assertEquals(v, c.getVoxel(Vector3(0, 1, 2)))
  }

  @Test
  fun `makeEmpty returns an empty chunk`() {
    val c = Chunk.makeEmpy()

    for (x in 0 until DEFAULT_CHUNK_SIZE) {
      for (y in 0 until DEFAULT_CHUNK_SIZE) {
        for (z in 0 until DEFAULT_CHUNK_SIZE) {
          Assertions.assertEquals(Voxel.EMPTY, c.getVoxel(Vector3(x, y, z)))
        }
      }
    }
  }
}