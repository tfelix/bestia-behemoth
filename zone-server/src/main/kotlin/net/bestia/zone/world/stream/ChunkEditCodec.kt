package net.bestia.zone.world.stream

import net.bestia.worldgen.derived.VoxelEdit
import net.bestia.worldgen.voxel.BlockType
import java.io.ByteArrayOutputStream

/**
 * The bytes of a batch of [VoxelEdit]s: `repeated: uvar indexGap, u8 blockId, u8 occupancy`, sorted by index.
 *
 * Gaps rather than indices because a column is index-contiguous, so nearly every gap is one byte and an edit
 * costs three.
 */
object ChunkEditCodec {

  /** Stored beside a payload, so a row written by another codec is recognised instead of misread. */
  const val FORMAT = 1

  /** @param edits sorted ascending, each index at most once */
  fun encode(edits: LongArray): ByteArray {
    val out = ByteArrayOutputStream(edits.size * 3)

    var previousIndex = 0
    for ((at, edit) in edits.withIndex()) {
      val index = VoxelEdit.indexOf(edit)
      require(at == 0 || index > previousIndex) { "Edits must be sorted and unique; $index followed $previousIndex" }

      writeVarInt(out, index - previousIndex)
      out.write(VoxelEdit.blockIdOf(edit))
      out.write(VoxelEdit.occupancyOf(edit))

      previousIndex = index
    }

    return out.toByteArray()
  }

  /** Refuses an unknown block id or air with material, so a corrupt payload fails here and not in a chunk. */
  fun decode(bytes: ByteArray): LongArray {
    val edits = ArrayList<Long>()
    var at = 0
    var index = 0

    while (at < bytes.size) {
      var gap = 0
      var shift = 0
      while (true) {
        require(at < bytes.size) { "Edits are truncated mid-index after ${edits.size} edits" }
        val b = bytes[at++].toInt() and 0xFF
        gap = gap or ((b and 0x7F) shl shift)
        if (b and 0x80 == 0) break
        shift += 7
        require(shift < 35) { "Varint in edits is longer than five bytes" }
      }

      require(at + 2 <= bytes.size) { "Edits end after the index of edit ${edits.size}, with no voxel" }

      index += gap
      val block = BlockType.of(bytes[at++].toInt() and 0xFF)
      val occupancy = bytes[at++].toInt() and 0xFF
      edits.add(VoxelEdit.pack(index, block, occupancy))
    }

    return edits.toLongArray()
  }

  private fun writeVarInt(out: ByteArrayOutputStream, value: Int) {
    require(value >= 0) { "Varints here are unsigned; got $value" }
    var remaining = value
    while (true) {
      if (remaining and 0x7F.inv() == 0) {
        out.write(remaining)
        return
      }
      out.write((remaining and 0x7F) or 0x80)
      remaining = remaining ushr 7
    }
  }
}
