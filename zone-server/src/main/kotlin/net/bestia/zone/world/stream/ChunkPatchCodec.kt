package net.bestia.zone.world.stream

import net.bestia.worldgen.derived.ChunkDelta
import java.io.ByteArrayOutputStream

/**
 * The packed removal list carried by a [ChunkPatchSMSG].
 *
 * ```
 * repeated: uvar indexDelta, u8 remainingOccupancy
 * ```
 *
 * Sorted by voxel index, and each index is written as the **gap since the previous one** rather than in full.
 * That is what makes a removal cheap: the vertical axis is contiguous
 * (`VoxelChunk.index`), so the voxels a brush takes out of one column are index-adjacent and nearly every gap
 * is 1, which is one byte. A column jump costs two. Measured against a sphere at the minimum bore radius the
 * whole thing runs about 2.5 bytes a removal, where an edit that also had to name a block cost five.
 *
 * ### The block id is derived, not omitted
 *
 * There is no building system, so the only terrain mutation is removal - and then the resulting block is
 * knowable without being sent: a voxel with material left still has the material the generator gave it, and one
 * carved to nothing is air. The client already holds the base, so it can say which.
 *
 * This replaces a note that used to argue the opposite, that *"block and occupancy travel together, always...
 * an edit format that can express one without the other is a format that can break [the invariant] in transit"*.
 * That was right for a format that could place arbitrary material. It is worth being precise about why the new
 * position is stronger rather than merely cheaper: the invariant that air has occupancy zero and everything else
 * does not is now **re-established independently on both sides** from data each side already had, instead of
 * being carried across the wire and re-checked on arrival. A patch has no way to express a violation of it.
 *
 * ### Removals are a map, not a log
 *
 * Keyed on voxel index, so a voxel worked repeatedly within one tick appears once, at the lowest occupancy it
 * reached. A player holding down a dig key does not get to multiply the broadcast.
 */
object ChunkPatchCodec {

  /**
   * Worst-case bytes for one removal: a three-byte index gap plus the occupancy.
   *
   * An upper bound, and the safe direction. A gap only needs three varint bytes if it exceeds 16 383, which
   * inside one chunk means jumping more than sixty-four columns between consecutive removals - the opposite of
   * what a brush produces. Overestimating makes [ChunkStreamSystem] send a snapshot where a patch would have
   * done, which is wasteful; underestimating would send a patch larger than the chunk it describes and call it
   * an optimisation.
   */
  const val MAX_BYTES_PER_REMOVAL = 4

  /**
   * @param removals packed `(voxelIndex shl 8) or remainingOccupancy`, the same packing [ChunkDelta] stores,
   *   **sorted ascending**
   */
  fun encode(removals: IntArray): ByteArray {
    val out = ByteArrayOutputStream(removals.size * 2)

    var previousIndex = 0
    for (entry in removals) {
      val index = ChunkDelta.indexOf(entry)
      require(index >= previousIndex) { "Removals must be sorted; $index followed $previousIndex" }

      writeVarInt(out, index - previousIndex)
      out.write(ChunkDelta.remainingOf(entry))

      previousIndex = index
    }

    return out.toByteArray()
  }

  /** Inverse of [encode]. Used by tests and by the cli-client; the game client decodes in C#. */
  fun decode(bytes: ByteArray): IntArray {
    val removals = ArrayList<Int>()
    var at = 0
    var index = 0

    while (at < bytes.size) {
      var gap = 0
      var shift = 0
      while (true) {
        require(at < bytes.size) { "Patch is truncated mid-index after ${removals.size} removals" }
        val b = bytes[at++].toInt() and 0xFF
        gap = gap or ((b and 0x7F) shl shift)
        if (b and 0x80 == 0) break
        shift += 7
        require(shift < 35) { "Varint in patch is longer than five bytes" }
      }

      require(at < bytes.size) {
        "Patch ends after the index of removal ${removals.size}, with no occupancy"
      }

      index += gap
      removals.add(ChunkDelta.pack(index, bytes[at++].toInt() and 0xFF))
    }

    return removals.toIntArray()
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
