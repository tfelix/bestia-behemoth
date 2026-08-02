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
 * ### A tighter format, measured and declined
 *
 * The zone-server's earlier chunk writer had a different idea for the occupancy half, and this note used to
 * say it was *"several times tighter"* and blocked by the palette size. Both halves of that were written
 * without measuring. They have now been measured, and neither survived.
 *
 * The scheme: occupancy is *derivable* from material everywhere except at a surface - air implies empty,
 * anything else implies full - so instead of a second stream, emit one merged run stream whose runs are
 * uniform in both, flag the runs that break the rule, and give only those an occupancy byte. Implemented
 * against 196 chunks of seed 8 at 192 cells:
 *
 * | format | bytes/chunk | deflated |
 * |---|---|---|
 * | two streams, as here | 19 383 | 2 263 |
 * | merged runs, sparse block ids | 14 629 (-24.5%) | 1 838 (-18.8%) |
 * | merged runs, densely renumbered ids | 14 629 (-24.5%) | - |
 *
 * So the real figure is **a fifth, not a factor**, and it is a fifth of a payload that deflate has already
 * taken to 2 KB - 425 bytes a chunk. That is not nothing, but it is not worth a wire format that three
 * modules and two languages have to agree on, and the storage model is proportional to what players edit
 * rather than to how big the world is, so the absolute bill it applies to is small. Ship it when there are
 * traffic numbers saying otherwise, which is what the architecture document asked for in the first place.
 *
 * **The palette was never the blocker.** That claim came from the original scheme packing material into the
 * low six bits of a flag byte. Writing the flag as the low bit of a *varint* material id instead has no
 * ceiling at all: ids under 64 still cost one byte, and an id above it costs two rather than being
 * unrepresentable. The third row above is the consequence - densely renumbering the palette to get under
 * sixty-four saves **exactly zero bytes**, because the only ids above it are ore, and ore is rare. The
 * grouping the gaps encode (basement 10-11, sedimentary 20-23, worked 60-67, ore 100+) is worth more than
 * a saving that measured as nothing, so [BlockType] keeps its gaps.
 */
object RleCodec {

  /**
   * 2 added the occupancy stream. Bumping this invalidates every cached and baked blob, by design.
   *
   * **Not reset to 1 with the stage versions**, and it is the only version number in the module that was
   * not. The others are development counters against a world nobody has generated twice; this one is a byte
   * written into every payload and named in the wire protocol - `CHUNK_ENCODING_RLE_V2` in `chunk.proto`,
   * `RleCodec.Version` in the client. Resetting it would rename an enum across three modules and force a
   * protobuf regeneration to say the same thing the format already says about itself, and the format did not
   * change. A number that is a real statement between two artefacts stays where it is.
   */
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
