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
  fun `rle round trips an arbitrary chunk including its occupancy`() {
    val chunk = VoxelChunk(pos, size = 8, height = 32)
    val rng = GenRng(17L)
    // Runs of random length, which is what terrain looks like: long spans with occasional changes. Occupancy
    // is deliberately random rather than mostly full, so the stream is exercised at its worst case instead of
    // the compressible one it will actually see.
    var i = 0
    val palette = BlockType.entries.toList()
    while (i < chunk.blocks.size) {
      val block = palette[rng.nextInt(palette.size)]
      val fill = if (block == BlockType.AIR) Occupancy.EMPTY else 1 + rng.nextInt(255)
      val run = 1 + rng.nextInt(40)
      for (k in 0 until run) {
        if (i + k >= chunk.blocks.size) break
        chunk.blocks[i + k] = block.id.toByte()
        chunk.occupancy[i + k] = fill.toByte()
      }
      i += run
    }

    val decoded = RleCodec.decode(pos, RleCodec.encode(chunk))

    assertEquals(chunk.size, decoded.size)
    assertEquals(chunk.height, decoded.height)
    assertTrue(chunk.blocks.contentEquals(decoded.blocks))
    assertTrue(chunk.occupancy.contentEquals(decoded.occupancy), "occupancy did not survive the round trip")
  }

  @Test
  fun `rle round trips a uniform chunk into a handful of bytes`() {
    // Deep underground and open sea are both single-value chunks, and there are a great many of them.
    // Streaming runs across the whole array rather than restarting per column is what makes them cheap.
    val chunk = VoxelChunk(pos, size = 32, height = 256)
    chunk.blocks.fill(BlockType.GRANITE.id.toByte())
    chunk.occupancy.fill(Occupancy.FULL_BYTE)

    val encoded = RleCodec.encode(chunk)

    assertTrue(encoded.size < 20, "a uniform chunk encoded to ${encoded.size} bytes")
    assertTrue(RleCodec.decode(pos, encoded).blocks.contentEquals(chunk.blocks))
    // One run in each stream. Adding occupancy cost this chunk four bytes, not a second copy of it - which is
    // the argument for two streams rather than interleaved pairs.
    assertEquals(2, RleCodec.runCount(chunk))
  }

  @Test
  fun `occupancy costs almost nothing on a chunk shaped like terrain`() {
    // The claim the two-stream layout rests on. Occupancy changes exactly where material does not: it is full
    // below the surface and empty above, so it is two runs per column no matter how many materials there are.
    val chunk = VoxelChunk(pos, size = 32, height = 64)
    val rng = GenRng(4242L)
    for (y in 0 until 32) {
      for (x in 0 until 32) {
        val ground = 20 + rng.nextInt(8)
        for (z in 0..ground) {
          // Several materials in the column, so the material stream has many runs and occupancy still has two.
          chunk[x, y, z] = if (z < ground - 3) BlockType.GRANITE else BlockType.DIRT
        }
        chunk.occupancy[chunk.index(x, y, ground)] = Occupancy.byteOf(rng.nextDouble())
      }
    }

    val encoded = RleCodec.encode(chunk)
    val materialOnly = RleCodec.encode(VoxelChunk(pos, 32, 64, chunk.blocks, ByteArray(chunk.volume).also {
      for (i in chunk.blocks.indices) if (chunk.blocks[i] != BlockType.AIR.id.toByte()) it[i] = Occupancy.FULL_BYTE
    }))

    // Random per-column fills are the pessimistic case - real surfaces vary smoothly - and even so the
    // occupancy stream stays a small fraction of the payload.
    assertTrue(
      encoded.size < materialOnly.size * 2,
      "occupancy more than doubled the payload: ${materialOnly.size} -> ${encoded.size}"
    )
  }

  @Test
  fun `a chunk refuses air with a fill and material without one`() {
    val chunk = VoxelChunk(pos, size = 2, height = 4)

    assertFailsWith<IllegalArgumentException> { chunk.set(0, 0, 0, BlockType.AIR, Occupancy.FULL) }
    assertFailsWith<IllegalArgumentException> { chunk.set(0, 0, 0, BlockType.GRANITE, Occupancy.EMPTY) }

    // Half a voxel of granite is fine; that is the whole point.
    chunk.set(0, 0, 0, BlockType.GRANITE, 128)
    assertEquals(0.502, chunk.fillAt(0, 0, 0), 1e-3)
  }

  @Test
  fun `decoding rejects a payload that breaks the air invariant`() {
    // The one place a chunk arrives from outside this process. An invariant that only holds for chunks we
    // generated ourselves is not an invariant, and every derived structure relies on this one.
    val chunk = VoxelChunk(pos, size = 2, height = 2)
    chunk.blocks.fill(BlockType.GRANITE.id.toByte())
    // Material everywhere, occupancy nowhere.
    val encoded = RleCodec.encode(chunk)

    assertFailsWith<IllegalStateException> { RleCodec.decode(pos, encoded) }
  }

  @Test
  fun `a partly filled voxel reports a fractional surface height`() {
    // What the whole change is for: the pipeline computes surface elevation as a continuous double, and this
    // is where that precision either survives into the voxels or is thrown away.
    val chunk = VoxelChunk(pos, size = 2, height = 8)
    for (z in 0..2) chunk[0, 0, z] = BlockType.GRANITE
    chunk.set(0, 0, 3, BlockType.GRASS, Occupancy.of(0.3))

    assertEquals(3, chunk.highestSolid(0, 0))
    assertEquals(3.3, chunk.solidHeightAt(0, 0), 0.01)
    // A full voxel's surface is its top, not its index: standing on voxel 2 puts you at 3.0.
    assertEquals(-1.0, chunk.solidHeightAt(1, 1), 1e-9)
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
    chunk.occupancy.fill(Occupancy.FULL_BYTE)
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
