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
 * ### Material and occupancy are two streams, one after the other
 *
 * Not interleaved pairs. Occupancy changes precisely where material does not - it is [Occupancy.FULL] for
 * every voxel below the air interface and [Occupancy.EMPTY] above it - so as its own stream it costs a few
 * runs per column, while interleaving would break every run in the material stream and roughly double the
 * payload. Neither stream needs a length prefix: both decode to exactly `size * size * height` entries, so
 * the first one ends where its count is reached.
 *
 * ```
 * u8   version
 * uvar size
 * uvar height
 * repeated to volume: uvar blockId,   uvar runLength
 * repeated to volume: uvar occupancy, uvar runLength
 * ```
 *
 * ### A tighter format, deliberately not adopted yet
 *
 * The zone-server's earlier chunk writer had a better idea for the occupancy half, worth recording because
 * the code it lived in is gone. Occupancy is *derivable* from material everywhere except at a surface - air
 * implies empty, anything else implies full - so instead of a second stream it flagged one bit per run and
 * emitted an occupancy byte only for the runs that break that rule. On terrain that is one extra byte per
 * column rather than a run pair, which is several times tighter than what is here.
 *
 * It is not adopted because the same scheme packed the material into the low six bits of the flag byte,
 * capping the palette at 64 blocks - and the palette is already past 60. Bit-packing the flags without that
 * ceiling means a wider header, at which point the saving is smaller than it looks. The architecture document
 * says to ship merged RLE first and optimise the wire format once there are real traffic numbers, so this
 * stays a note until there are.
 */
object RleCodec {

  /** 2 added the occupancy stream. Bumping this invalidates every cached and baked blob, by design. */
  const val VERSION = 2

  fun encode(chunk: VoxelChunk): ByteArray {
    val out = ByteArrayOutputStream(1024)
    out.write(VERSION)
    writeVarInt(out, chunk.size)
    writeVarInt(out, chunk.height)

    writeRuns(out, chunk.blocks)
    writeRuns(out, chunk.occupancy)

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
    val volume = size * size * height

    val blocks = readRuns(cursor, volume, chunk, "blocks")
    val occupancy = readRuns(cursor, volume, chunk, "occupancy")

    require(!cursor.hasMore) {
      "Chunk $chunk has trailing bytes after both streams decoded; the payload is not what it claims"
    }

    val decoded = VoxelChunk(chunk, size, height, blocks, occupancy)
    // Cheap next to the two fills above, and this is the one place a chunk arrives from outside this
    // process - out of a blob store, and later off a socket. An invariant that only holds for chunks we
    // generated ourselves is not an invariant.
    decoded.validate()
    return decoded
  }

  /** How many runs a chunk encodes to, for deciding whether a delta is worth baking. */
  fun runCount(chunk: VoxelChunk): Int = runsIn(chunk.blocks) + runsIn(chunk.occupancy)

  private fun writeRuns(out: ByteArrayOutputStream, values: ByteArray) {
    var i = 0
    while (i < values.size) {
      val value = values[i]
      var run = 1
      while (i + run < values.size && values[i + run] == value) run++

      writeVarInt(out, value.toInt() and 0xFF)
      writeVarInt(out, run)
      i += run
    }
  }

  private fun readRuns(cursor: Cursor, volume: Int, chunk: ChunkPos, what: String): ByteArray {
    val values = ByteArray(volume)

    var written = 0
    while (written < volume) {
      require(cursor.hasMore) {
        "Chunk $chunk $what stream ends after $written of $volume entries; the payload is truncated"
      }
      val value = cursor.readVarInt()
      val run = cursor.readVarInt()

      require(value in 0..255) { "Chunk $chunk $what contains $value, which cannot be a byte" }
      require(run > 0) { "Chunk $chunk $what contains a run of length $run" }
      require(written + run <= volume) {
        "Chunk $chunk $what decodes to more than $volume entries; the payload is corrupt"
      }

      java.util.Arrays.fill(values, written, written + run, value.toByte())
      written += run
    }

    return values
  }

  private fun runsIn(values: ByteArray): Int {
    if (values.isEmpty()) return 0

    var runs = 1
    for (i in 1 until values.size) {
      if (values[i] != values[i - 1]) runs++
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
