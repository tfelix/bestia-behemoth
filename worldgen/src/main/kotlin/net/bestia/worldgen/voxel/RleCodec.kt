package net.bestia.worldgen.voxel

import net.bestia.worldgen.core.ChunkPos
import java.io.ByteArrayOutputStream

/**
 * Run-length codec for [VoxelChunk].
 *
 * Runs stream across the whole chunk in array order rather than restarting per column. Because the
 * layout puts the vertical axis contiguous, that means a run can carry on from the top of one column
 * into the bottom of the next - so a chunk of open sea, or one deep underground, encodes as a handful
 * of bytes instead of one run per column. On real terrain the difference between the two schemes is
 * about a factor of three, and it is entirely free.
 *
 * The format is versioned and self-describing about its dimensions. Both matter for a format that ends
 * up in an object store: a chunk read back six months later must either decode correctly or say plainly
 * that it cannot, and "silently reinterpret with the current chunk size" is not one of those two.
 *
 * ```
 * u8   version
 * uvar size
 * uvar height
 * repeated: uvar blockId, uvar runLength
 * ```
 */
object RleCodec {

  const val VERSION = 1

  fun encode(chunk: VoxelChunk): ByteArray {
    val out = ByteArrayOutputStream(1024)
    out.write(VERSION)
    writeVarInt(out, chunk.size)
    writeVarInt(out, chunk.height)

    val blocks = chunk.blocks
    var i = 0
    while (i < blocks.size) {
      val value = blocks[i]
      var run = 1
      while (i + run < blocks.size && blocks[i + run] == value) run++

      writeVarInt(out, value.toInt() and 0xFF)
      writeVarInt(out, run)
      i += run
    }

    return out.toByteArray()
  }

  fun decode(chunk: ChunkPos, bytes: ByteArray): VoxelChunk {
    val cursor = Cursor(bytes)

    val version = cursor.readByte()
    require(version == VERSION) {
      "Chunk $chunk was encoded with RLE version $version, this build reads version $VERSION"
    }

    val size = cursor.readVarInt()
    val height = cursor.readVarInt()
    val blocks = ByteArray(size * size * height)

    var written = 0
    while (cursor.hasMore) {
      val id = cursor.readVarInt()
      val run = cursor.readVarInt()

      require(id in 0..255) { "Chunk $chunk contains block id $id, which cannot be a byte" }
      require(run > 0) { "Chunk $chunk contains a run of length $run" }
      require(written + run <= blocks.size) {
        "Chunk $chunk decodes to more than ${blocks.size} blocks; the payload is truncated or corrupt"
      }

      java.util.Arrays.fill(blocks, written, written + run, id.toByte())
      written += run
    }

    require(written == blocks.size) {
      "Chunk $chunk decoded to $written of ${blocks.size} blocks"
    }

    return VoxelChunk(chunk, size, height, blocks)
  }

  /** How many runs a chunk encodes to, for deciding whether a delta is worth baking. */
  fun runCount(chunk: VoxelChunk): Int {
    val blocks = chunk.blocks
    if (blocks.isEmpty()) return 0

    var runs = 1
    for (i in 1 until blocks.size) {
      if (blocks[i] != blocks[i - 1]) runs++
    }
    return runs
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

  private class Cursor(private val bytes: ByteArray) {
    private var at = 0

    val hasMore get() = at < bytes.size

    fun readByte(): Int {
      require(at < bytes.size) { "Truncated chunk payload" }
      return bytes[at++].toInt() and 0xFF
    }

    fun readVarInt(): Int {
      var result = 0
      var shift = 0
      while (true) {
        val b = readByte()
        result = result or ((b and 0x7F) shl shift)
        if (b and 0x80 == 0) return result
        shift += 7
        require(shift < 35) { "Varint in chunk payload is longer than five bytes" }
      }
    }
  }
}
