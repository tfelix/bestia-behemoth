package net.bestia.zone.world.stream

import java.io.ByteArrayOutputStream

/**
 * The packed edit list carried by a [ChunkPatchSMSG].
 *
 * ```
 * repeated: uvar voxelIndex, u8 blockId, u8 occupancy
 * ```
 *
 * At most five bytes per edit: a voxel index tops out at 262 143, which needs three varint bytes, plus the
 * two fixed bytes. Edits low in a chunk are cheaper - an index under 16 384 takes two bytes, so the first
 * sixty-four columns cost four. `ChunkDelta.estimatedBytes` uses the same five, so the wire cost of an edit
 * and the server's own idea of it agree by construction.
 *
 * [BYTES_PER_EDIT] is therefore an **upper bound**, and every sizing decision made with it errs towards
 * calling a patch bigger than it is. That is the safe direction: overestimating sends a snapshot where a
 * patch would have done, which is merely wasteful, while underestimating would send a patch larger than the
 * chunk it describes and call it an optimisation.
 *
 * ### Block and occupancy travel together, always
 *
 * Never occupancy alone, never block alone, even though occupancy is derivable from material everywhere
 * except at a surface. The chunk invariant is that air has occupancy zero and everything else does not, and
 * an edit format that can express one without the other is a format that can break it in transit. The
 * server packs the pair into a single int for exactly this reason, and this mirrors that.
 *
 * ### Edits are a map, not a log
 *
 * Keyed on voxel index, so a voxel edited repeatedly within one tick appears once with its final value.
 * A player holding down a dig key does not get to multiply the broadcast.
 */
object ChunkPatchCodec {

  /** Worst-case bytes for one edit at chunk scale. See the class note - an upper bound, not a fixed size. */
  const val BYTES_PER_EDIT = 5

  /**
   * @param edits voxel index to `(blockId shl 8) or occupancy`, the same packing `ChunkDelta` stores
   */
  fun encode(edits: Map<Int, Int>): ByteArray {
    val out = ByteArrayOutputStream(edits.size * BYTES_PER_EDIT)

    // Sorted so the same edit set always produces the same bytes. A patch is compared in tests and may
    // one day be cached or hashed; an iteration-order-dependent payload would make both unreliable.
    edits.entries.sortedBy { it.key }.forEach { (index, packed) ->
      writeVarInt(out, index)
      out.write((packed ushr 8) and 0xFF)
      out.write(packed and 0xFF)
    }

    return out.toByteArray()
  }

  /** Inverse of [encode]. Used by tests and by the cli-client; the game client decodes in C#. */
  fun decode(bytes: ByteArray): Map<Int, Int> {
    val edits = LinkedHashMap<Int, Int>()
    var at = 0

    while (at < bytes.size) {
      var index = 0
      var shift = 0
      while (true) {
        require(at < bytes.size) { "Patch is truncated mid-index after ${edits.size} edits" }
        val b = bytes[at++].toInt() and 0xFF
        index = index or ((b and 0x7F) shl shift)
        if (b and 0x80 == 0) break
        shift += 7
        require(shift < 35) { "Varint in patch is longer than five bytes" }
      }

      require(at + 2 <= bytes.size) {
        "Patch ends after the index of edit ${edits.size}, with no block and occupancy"
      }
      val blockId = bytes[at++].toInt() and 0xFF
      val occupancy = bytes[at++].toInt() and 0xFF

      edits[index] = (blockId shl 8) or occupancy
    }

    return edits
  }

  fun pack(blockId: Int, occupancy: Int): Int {
    require(blockId in 0..255) { "Block id $blockId does not fit a byte" }
    require(occupancy in 0..255) { "Occupancy $occupancy does not fit a byte" }
    return (blockId shl 8) or occupancy
  }

  fun blockIdOf(packed: Int) = (packed ushr 8) and 0xFF

  fun occupancyOf(packed: Int) = packed and 0xFF

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
