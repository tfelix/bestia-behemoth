package net.bestia.zone.world.stream

import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChunkPatchCodecTest {

  @Test
  fun `edits survive a round trip`() {
    val edits = mapOf(
      0 to ChunkPatchCodec.pack(BlockType.AIR.id, Occupancy.EMPTY),
      1 to ChunkPatchCodec.pack(BlockType.GRASS.id, Occupancy.FULL),
      262_143 to ChunkPatchCodec.pack(BlockType.MASONRY.id, 128)
    )

    assertEquals(edits, ChunkPatchCodec.decode(ChunkPatchCodec.encode(edits)))
  }

  @Test
  fun `the highest voxel index in a chunk still fits five bytes per edit`() {
    // 32 x 32 x 256 - 1. The sizing of every patch-versus-snapshot decision assumes this, so if a chunk ever
    // grows past what three varint bytes hold, that assumption should fail here rather than silently drift.
    val encoded = ChunkPatchCodec.encode(mapOf(262_143 to ChunkPatchCodec.pack(1, 1)))

    assertEquals(ChunkPatchCodec.BYTES_PER_EDIT, encoded.size)
  }

  @Test
  fun `the same edits always produce the same bytes`() {
    val forwards = linkedMapOf(1 to 0x0101, 2 to 0x0202, 3 to 0x0303)
    val backwards = linkedMapOf(3 to 0x0303, 2 to 0x0202, 1 to 0x0101)

    assertTrue(
      ChunkPatchCodec.encode(forwards).contentEquals(ChunkPatchCodec.encode(backwards)),
      "insertion order must not reach the wire - a payload that depends on it cannot be compared or cached"
    )
  }

  @Test
  fun `block and occupancy cannot be separated`() {
    val packed = ChunkPatchCodec.pack(BlockType.SAND.id, 200)

    assertEquals(BlockType.SAND.id, ChunkPatchCodec.blockIdOf(packed))
    assertEquals(200, ChunkPatchCodec.occupancyOf(packed))
  }

  @Test
  fun `a truncated patch is refused rather than half applied`() {
    val whole = ChunkPatchCodec.encode(mapOf(700 to ChunkPatchCodec.pack(BlockType.DIRT.id, Occupancy.FULL)))

    // Half an edit is not a smaller edit set - applying the part that parsed would leave the client's chunk
    // silently disagreeing with the server's, which is the one outcome the revision check exists to prevent.
    assertFailsWith<IllegalArgumentException> {
      ChunkPatchCodec.decode(whole.copyOfRange(0, whole.size - 1))
    }
  }

  @Test
  fun `a value that cannot fit a byte is refused at pack time`() {
    assertFailsWith<IllegalArgumentException> { ChunkPatchCodec.pack(256, 0) }
    assertFailsWith<IllegalArgumentException> { ChunkPatchCodec.pack(0, 256) }
  }
}
