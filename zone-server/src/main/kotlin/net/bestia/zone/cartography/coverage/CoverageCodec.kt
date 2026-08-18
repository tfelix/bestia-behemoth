package net.bestia.zone.cartography.coverage

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * The stored form of a [Coverage]: what goes in a chart row.
 *
 * ```
 * u8   version
 * uvar cellMetres          plain, so a format check costs no inflation
 * uvar cellsAcross
 * uvar cellsDown
 * deflate {
 *   uvar blockCount
 *   repeat blockCount:
 *     uvar keyDelta        gap since the previous block key, ascending
 *     u64 x 64             one little-endian word per row
 * }
 * ```
 *
 * ### Why the header is outside the compression, and what it is for
 *
 * The three numbers in front are the *meaning* of the bits that follow. A cell index is a position, so the same
 * bit says different things at a different cell size or in a differently sized world, and a chart read under the
 * wrong one would confidently show its owner somewhere they have never been. Nothing in the bits themselves
 * would reveal that. Writing the three down makes the mismatch detectable with [isReadableBy], which a chart
 * loader can ask before deciding to discard the row - the same posture `WorldObjectDivergence` takes towards
 * world objects whose terrain has been regenerated under them.
 *
 * They sit outside the deflate stream so that check is four byte reads rather than a decompression.
 *
 * ### Why deflate at all, and why the keys are delta-coded
 *
 * The two shapes coverage takes are both compressible and for opposite reasons. A survey disc is a solid region,
 * so its blocks are long runs of `0xFF` - which is what deflate is best at, and it takes a 512 byte block down
 * to a couple of dozen. A frontier is sparse, so its blocks are mostly zero, which compresses just as well.
 * Measured on a 5 km survey the whole blob comes out in the low hundreds of bytes against 8 kB of raw blocks.
 *
 * The keys are the one part deflate cannot help with, since a block key is 8 bytes of high-entropy packing.
 * Sorted and stored as gaps they are one byte each within a block column and five at a column change, so a
 * thousand-block chart pays about 2 kB for its index instead of 8 kB.
 */
object CoverageCodec {

  const val VERSION = 1

  fun encode(coverage: Coverage): ByteArray {
    val out = ByteArrayOutputStream(INITIAL_BYTES)

    out.write(VERSION)
    writeVarLong(out, SurveyGrid.CELL_METRES.toLong())
    writeVarLong(out, coverage.grid.cellsAcross.toLong())
    writeVarLong(out, coverage.grid.cellsDown.toLong())

    val entries = coverage.blockEntries()
    val deflater = Deflater(Deflater.BEST_COMPRESSION)
    try {
      DeflaterOutputStream(out, deflater).use { body ->
        writeVarLong(body, entries.size.toLong())

        var previousKey = 0L
        for ((key, rows) in entries) {
          writeVarLong(body, key - previousKey)
          previousKey = key

          for (bits in rows) {
            for (byte in 0 until 8) body.write(((bits ushr (byte * 8)) and 0xFF).toInt())
          }
        }
      }
    } finally {
      // DeflaterOutputStream does not free the native buffers of a Deflater it did not create.
      deflater.end()
    }

    return out.toByteArray()
  }

  /**
   * Whether a blob's cell size and world dimensions are the ones [grid] uses.
   *
   * False means the row predates a change to either and its bits no longer name the places they were written
   * for. Ask before [decode], which refuses rather than guesses.
   */
  fun isReadableBy(bytes: ByteArray, grid: SurveyGrid): Boolean {
    if (bytes.size < MIN_BYTES || bytes[0].toInt() != VERSION) return false

    val reader = Reader(bytes, 1)
    return try {
      reader.varLong() == SurveyGrid.CELL_METRES.toLong() &&
          reader.varLong() == grid.cellsAcross.toLong() &&
          reader.varLong() == grid.cellsDown.toLong()
    } catch (e: IllegalArgumentException) {
      false
    }
  }

  /** Inverse of [encode]. Throws on anything it does not recognise; check [isReadableBy] first. */
  fun decode(bytes: ByteArray, grid: SurveyGrid): Coverage {
    require(bytes.size >= MIN_BYTES) { "A coverage blob is at least $MIN_BYTES bytes, got ${bytes.size}" }
    require(bytes[0].toInt() == VERSION) { "Coverage version ${bytes[0].toInt()}, expected $VERSION" }

    val header = Reader(bytes, 1)
    val cellMetres = header.varLong()
    val cellsAcross = header.varLong()
    val cellsDown = header.varLong()

    require(cellMetres == SurveyGrid.CELL_METRES.toLong()) {
      "Coverage was written at $cellMetres m per cell, the grid is ${SurveyGrid.CELL_METRES.toLong()} m - " +
          "every bit in it means a different place"
    }
    require(cellsAcross == grid.cellsAcross.toLong() && cellsDown == grid.cellsDown.toLong()) {
      "Coverage was written for a ${cellsAcross}x$cellsDown cell world, the grid is " +
          "${grid.cellsAcross}x${grid.cellsDown}"
    }

    val body = InflaterInputStream(bytes.inputStream(header.at, bytes.size - header.at)).use { it.readBytes() }
    val reader = Reader(body, 0)
    val blockCount = reader.varLong()
    require(blockCount >= 0 && blockCount <= MAX_BLOCKS) { "Coverage claims $blockCount blocks" }

    val coverage = Coverage(grid)
    var key = 0L
    for (index in 0 until blockCount) {
      key += reader.varLong()

      val rows = LongArray(Coverage.BLOCK_CELLS)
      for (row in rows.indices) {
        var bits = 0L
        for (byte in 0 until 8) bits = bits or (reader.byte().toLong() shl (byte * 8))
        rows[row] = bits
      }

      coverage.putBlock(key, rows)
    }

    return coverage
  }

  private fun writeVarLong(out: java.io.OutputStream, value: Long) {
    require(value >= 0) { "Varints here are non-negative by construction, got $value" }

    var remaining = value
    while (remaining >= 0x80) {
      out.write(((remaining and 0x7F) or 0x80).toInt())
      remaining = remaining ushr 7
    }
    out.write(remaining.toInt())
  }

  /** A cursor over a byte array. Nested because nothing outside this codec reads these two encodings. */
  private class Reader(private val bytes: ByteArray, var at: Int) {

    fun byte(): Int {
      require(at < bytes.size) { "Coverage blob ended early at $at" }
      return bytes[at++].toInt() and 0xFF
    }

    fun varLong(): Long {
      var value = 0L
      var shift = 0
      while (true) {
        val byte = byte()
        value = value or ((byte and 0x7F).toLong() shl shift)
        if (byte and 0x80 == 0) return value

        shift += 7
        require(shift < Long.SIZE_BITS) { "Varint at $at does not terminate" }
      }
    }
  }

  /** Version plus three one-byte varints: the shortest header a blob can have. */
  private const val MIN_BYTES = 4

  /** A whole 4096 km world is a million blocks. Anything past that is a corrupt length, not a large chart. */
  private const val MAX_BLOCKS = 4_000_000L

  private const val INITIAL_BYTES = 512
}
