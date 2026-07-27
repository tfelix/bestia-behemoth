package net.bestia.worldgen.voxel

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.GenRng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VoxelTest {

  private val pos = ChunkPos(3, -2, 1)

  @Test
  fun `block ids are unique and survive a round trip`() {
    // The ids go into the wire format and into the base hash the client checks against the server, so a
    // collision or a renumbering is a data-format break rather than a bug.
    val ids = BlockType.entries.map { it.id }
    assertEquals(ids.size, ids.toSet().size, "duplicate block ids")

    for (block in BlockType.entries) {
      assertEquals(block, BlockType.of(block.id))
    }
    assertFailsWith<IllegalArgumentException> { BlockType.of(199) }
  }

  @Test
  fun `a chunk addresses its columns contiguously in the vertical`() {
    // The layout RLE compression and every column scan depend on.
    val chunk = VoxelChunk(pos, size = 4, height = 8)

    assertEquals(chunk.index(2, 3, 0) + 1, chunk.index(2, 3, 1))
    assertEquals(8, chunk.columnOffset(1, 0) - chunk.columnOffset(0, 0))
  }

  @Test
  fun `highest solid ignores water and highest non-air does not`() {
    val chunk = VoxelChunk(pos, size = 2, height = 10)
    for (z in 0..3) chunk[0, 0, z] = BlockType.GRANITE
    for (z in 4..6) chunk[0, 0, z] = BlockType.WATER

    assertEquals(3, chunk.highestSolid(0, 0))
    assertEquals(6, chunk.highestNonAir(0, 0))

    // An untouched column is all air, which is a legitimate state and must not report a block.
    assertEquals(-1, chunk.highestSolid(1, 1))
    assertEquals(-1, chunk.highestNonAir(1, 1))
  }

  @Test
  fun `rle round trips an arbitrary chunk`() {
    val chunk = VoxelChunk(pos, size = 8, height = 32)
    val rng = GenRng(17L)
    // Runs of random length, which is what terrain looks like: long spans with occasional changes.
    var i = 0
    val palette = BlockType.entries.toList()
    while (i < chunk.blocks.size) {
      val block = palette[rng.nextInt(palette.size)]
      val run = 1 + rng.nextInt(40)
      for (k in 0 until run) {
        if (i + k >= chunk.blocks.size) break
        chunk.blocks[i + k] = block.id.toByte()
      }
      i += run
    }

    val decoded = RleCodec.decode(pos, RleCodec.encode(chunk))

    assertEquals(chunk.size, decoded.size)
    assertEquals(chunk.height, decoded.height)
    assertTrue(chunk.blocks.contentEquals(decoded.blocks))
  }

  @Test
  fun `rle round trips a uniform chunk into a handful of bytes`() {
    // Deep underground and open sea are both single-value chunks, and there are a great many of them.
    // Streaming runs across the whole array rather than restarting per column is what makes them cheap.
    val chunk = VoxelChunk(pos, size = 32, height = 256)
    chunk.blocks.fill(BlockType.GRANITE.id.toByte())

    val encoded = RleCodec.encode(chunk)

    assertTrue(encoded.size < 16, "a uniform chunk encoded to ${encoded.size} bytes")
    assertTrue(RleCodec.decode(pos, encoded).blocks.contentEquals(chunk.blocks))
    assertEquals(1, RleCodec.runCount(chunk))
  }

  @Test
  fun `rle rejects a payload from another version`() {
    val chunk = VoxelChunk(pos, size = 2, height = 2)
    val encoded = RleCodec.encode(chunk)
    encoded[0] = 99

    // Loudly wrong beats silently reinterpreted: a chunk read back after a format change must either
    // decode correctly or say it cannot.
    assertFailsWith<IllegalArgumentException> { RleCodec.decode(pos, encoded) }
  }

  @Test
  fun `rle rejects a truncated payload`() {
    val chunk = VoxelChunk(pos, size = 4, height = 4)
    chunk.blocks.fill(BlockType.SHALE.id.toByte())
    val encoded = RleCodec.encode(chunk)

    assertFailsWith<IllegalArgumentException> {
      RleCodec.decode(pos, encoded.copyOf(encoded.size - 1))
    }
  }

  @Test
  fun `the vertical voxel mapping is consistent and puts index zero at sea level`() {
    val config = net.bestia.worldgen.core.WorldConfig(
      seed = 1L, widthCells = 4, heightCells = 4, chunkSize = 8, chunkHeight = 16, voxelSize = 1.0
    )

    assertEquals(0, config.voxelZOf(0.5))
    assertEquals(-1, config.voxelZOf(-0.5), "the voxel below sea level is index -1, not 0")
    assertEquals(0, config.chunkZOf(5.0))
    assertEquals(-1, config.chunkZOf(-1.0), "flooring must go the right way for negative elevations")
    assertEquals(1, config.chunkZOf(20.0))
    assertEquals(16.0, config.elevationOfVoxel(config.voxelBaseOf(ChunkPos(0, 0, 1))), 1e-12)
  }
}
