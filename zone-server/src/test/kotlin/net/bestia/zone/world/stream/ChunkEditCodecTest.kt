package net.bestia.zone.world.stream

import net.bestia.worldgen.derived.VoxelEdit
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChunkEditCodecTest {

  @Test
  fun `edits survive a round trip`() {
    val edits = longArrayOf(
      VoxelEdit.pack(0, BlockType.AIR, Occupancy.EMPTY),
      VoxelEdit.pack(1, BlockType.WATER, 128),
      VoxelEdit.pack(262_143, BlockType.GRANITE, Occupancy.FULL)
    )

    assertContentEquals(edits, ChunkEditCodec.decode(ChunkEditCodec.encode(edits)))
  }

  @Test
  fun `a run of adjacent edits costs three bytes each`() {
    val column = LongArray(64) { VoxelEdit.pack(10_000 + it, BlockType.WATER, Occupancy.FULL) }

    val encoded = ChunkEditCodec.encode(column)

    // The first index is absolute and needs two varint bytes at 10 000; the other 63 are gaps of one.
    assertEquals(4 + 63 * 3, encoded.size)
  }

  @Test
  fun `unsorted or repeated edits are refused`() {
    val later = VoxelEdit.pack(3, BlockType.AIR, Occupancy.EMPTY)
    val earlier = VoxelEdit.pack(1, BlockType.AIR, Occupancy.EMPTY)

    assertFailsWith<IllegalArgumentException> { ChunkEditCodec.encode(longArrayOf(later, earlier)) }
    assertFailsWith<IllegalArgumentException> { ChunkEditCodec.encode(longArrayOf(later, later)) }
  }

  @Test
  fun `a truncated payload is refused`() {
    val encoded = ChunkEditCodec.encode(longArrayOf(VoxelEdit.pack(5, BlockType.WATER, 200)))

    assertFailsWith<IllegalArgumentException> { ChunkEditCodec.decode(encoded.copyOf(encoded.size - 1)) }
  }

  @Test
  fun `air with material is refused on decode`() {
    // Gap 5, block AIR, occupancy 200.
    val corrupt = byteArrayOf(5, BlockType.AIR.id.toByte(), 200.toByte())

    assertFailsWith<IllegalArgumentException> { ChunkEditCodec.decode(corrupt) }
  }

  @Test
  fun `an unknown block id is refused on decode`() {
    val corrupt = byteArrayOf(5, 250.toByte(), 1)

    assertFailsWith<IllegalArgumentException> { ChunkEditCodec.decode(corrupt) }
  }
}
