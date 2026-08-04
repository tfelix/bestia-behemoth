package net.bestia.zone.world.stream

import net.bestia.worldgen.derived.ChunkDelta
import net.bestia.worldgen.voxel.Occupancy
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChunkPatchCodecTest {

  @Test
  fun `removals survive a round trip`() {
    val removals = intArrayOf(
      ChunkDelta.pack(0, Occupancy.EMPTY),
      ChunkDelta.pack(1, 128),
      ChunkDelta.pack(262_143, Occupancy.FULL - 1)
    )

    assertContentEquals(removals, ChunkPatchCodec.decode(ChunkPatchCodec.encode(removals)))
  }

  /**
   * The gap coding is what makes a removal cheap, and a brush is what makes the gaps small.
   *
   * The vertical axis is contiguous, so the voxels a brush takes out of one column are index-adjacent: after the
   * first, every gap is 1 and costs one byte, so a removal is two bytes rather than the five an edit carrying a
   * block id took. This is the number `StorageBudgetTest` defends on the storage side.
   */
  @Test
  fun `a run of adjacent removals costs two bytes each`() {
    val column = IntArray(64) { ChunkDelta.pack(10_000 + it, Occupancy.EMPTY) }

    val encoded = ChunkPatchCodec.encode(column)

    // The first index is absolute and needs two varint bytes at 10 000; the other 63 are gaps of one.
    assertEquals(3 + 63 * 2, encoded.size)
    assertContentEquals(column, ChunkPatchCodec.decode(encoded))
  }

  @Test
  fun `the highest voxel index in a chunk still fits the worst case bound`() {
    // 32 x 32 x 256 - 1, as one removal, so its index is written absolutely: three varint bytes plus the
    // occupancy. If a chunk ever grows past what three varint bytes hold, the bound the patch-versus-snapshot
    // sizing is documented against should fail here rather than silently drift.
    val encoded = ChunkPatchCodec.encode(intArrayOf(ChunkDelta.pack(262_143, 1)))

    assertEquals(ChunkPatchCodec.MAX_BYTES_PER_REMOVAL, encoded.size)
  }

  /**
   * Sorted order is a contract, not a convenience, and the codec refuses to guess.
   *
   * Gap coding cannot express a backwards step, so an unsorted batch is not merely less compressible - it is
   * unrepresentable. Refusing here means the mistake surfaces at the boundary rather than as a patch that
   * decodes to the wrong voxels.
   */
  @Test
  fun `an unsorted batch is refused rather than encoded wrongly`() {
    assertFailsWith<IllegalArgumentException> {
      ChunkPatchCodec.encode(
        intArrayOf(ChunkDelta.pack(3, 0), ChunkDelta.pack(1, 0))
      )
    }
  }

  @Test
  fun `the same removals always produce the same bytes`() {
    val removals = intArrayOf(ChunkDelta.pack(1, 10), ChunkDelta.pack(2, 20), ChunkDelta.pack(3, 30))

    assertTrue(
      ChunkPatchCodec.encode(removals).contentEquals(ChunkPatchCodec.encode(removals.copyOf())),
      "a payload that depends on anything but the removals cannot be compared or cached"
    )
  }

  /**
   * A removal names no block, and that is the point rather than an omission.
   *
   * Under removal-only the material is derivable: a voxel with occupancy left keeps what the generator gave it,
   * and one at zero is air. So the invariant that air is empty and everything else is not is re-established
   * independently on each side instead of being carried across - which means a patch cannot express a violation
   * of it at all. This replaces a test that asserted the opposite property, that block and occupancy could
   * never be separated; that property was correct for a format able to place arbitrary material.
   */
  @Test
  fun `a removal carries an index and an occupancy and nothing else`() {
    val encoded = ChunkPatchCodec.encode(intArrayOf(ChunkDelta.pack(5, 200)))

    assertEquals(2, encoded.size, "one small index gap and one occupancy byte")
    assertEquals(5, encoded[0].toInt(), "the first index is absolute")
    assertEquals(200, encoded[1].toInt() and 0xFF)
  }

  @Test
  fun `a truncated patch is refused rather than half applied`() {
    val whole = ChunkPatchCodec.encode(intArrayOf(ChunkDelta.pack(700, Occupancy.FULL)))

    // Half a removal is not a smaller removal set - applying the part that parsed would leave the client's
    // chunk silently disagreeing with the server's, which is the one outcome the revision check exists to
    // prevent.
    assertFailsWith<IllegalArgumentException> {
      ChunkPatchCodec.decode(whole.copyOfRange(0, whole.size - 1))
    }
  }

  @Test
  fun `a value that cannot fit its field is refused at pack time`() {
    assertFailsWith<IllegalArgumentException> { ChunkDelta.pack(0, 256) }
    assertFailsWith<IllegalArgumentException> { ChunkDelta.pack(-1, 0) }
  }
}
