package net.bestia.worldgen.lod

import kotlin.math.roundToInt

/**
 * Turns a [SurfacePatch] into bytes and back.
 *
 * ### Four planes, not one interleaved stream
 *
 * `RleCodec` separates its block and occupancy streams and measures the difference as roughly a factor of
 * two; the same argument applies here with more force, because these four values have nothing in common.
 * Heights next to heights are nearly equal and deflate to almost nothing, and so do long runs of one block
 * id or one canopy value. Interleaved, every fourth byte breaks the run.
 *
 * Measured on the reference world: a payload is a fixed 25 364 bytes and the worst of eighty-one patches
 * deflates to **2 179**, against roughly 192 kB for the sixty-four chunks that patch stands in for. Most
 * deflate to a few hundred. `SurfacePatchTest` holds the bound.
 *
 * ### Heights are quarter-metres above the patch floor
 *
 * A patch spans at most two kilometres, so its own relief fits in sixteen bits at a quarter-metre step with
 * a very great deal to spare - and a quarter of a metre is a twelfth of a sample spacing at the finest
 * level, which is far below what a slope four metres wide can show. Storing the floor once per patch rather
 * than absolute elevations per sample is also what keeps neighbouring heights numerically close, which is
 * what the deflate above is relying on.
 */
object SurfacePatchCodec {

  /**
   * 1 is the first format. Written into every payload and named in the wire protocol as
   * `SURFACE_PATCH_ENCODING_PLANES_V1`, so it moves only when the bytes do.
   */
  const val VERSION = 1

  /** Quarter of a metre. See the class KDoc for why that is enough. */
  const val HEIGHT_STEP = 0.25

  /** Reserved in the water plane for a dry sample, which is why heights are stored from one rather than zero. */
  private const val DRY = 0

  fun encode(patch: SurfacePatch): ByteArray {
    val samples = PatchGrid.SAMPLES * PatchGrid.SAMPLES

    // The floor is taken over water as well as ground: a sea floor far below a waterline still has to encode,
    // and picking the minimum of both is one comparison against a whole second failure mode.
    var floor = Double.POSITIVE_INFINITY
    for (i in 0 until samples) {
      floor = minOf(floor, patch.height[i].toDouble())
      if (!patch.water[i].isNaN()) floor = minOf(floor, patch.water[i].toDouble())
    }

    val floorSteps = Math.floor(floor / HEIGHT_STEP).toInt()
    val out = ByteArray(HEADER_BYTES + samples * BYTES_PER_SAMPLE)
    var at = 0

    out[at++] = VERSION.toByte()
    out[at++] = patch.pos.level.toByte()
    at = putInt(out, at, patch.pos.x)
    at = putInt(out, at, patch.pos.y)
    at = putInt(out, at, floorSteps)

    for (i in 0 until samples) at = putShort(out, at, steps(patch.height[i], floorSteps))
    for (i in 0 until samples) {
      val water = patch.water[i]
      at = putShort(out, at, if (water.isNaN()) DRY else steps(water, floorSteps) + 1)
    }
    for (i in 0 until samples) out[at++] = patch.block[i]
    for (i in 0 until samples) out[at++] = patch.canopy[i]

    return out
  }

  fun decode(bytes: ByteArray): SurfacePatch {
    val samples = PatchGrid.SAMPLES * PatchGrid.SAMPLES
    require(bytes.size == HEADER_BYTES + samples * BYTES_PER_SAMPLE) {
      "a patch payload is ${HEADER_BYTES + samples * BYTES_PER_SAMPLE} bytes, got ${bytes.size}"
    }

    var at = 0
    val version = bytes[at++].toInt() and 0xFF
    require(version == VERSION) {
      "patch encoded with surface-patch version $version, this build reads version $VERSION"
    }

    val level = bytes[at++].toInt() and 0xFF
    val x = getInt(bytes, at); at += 4
    val y = getInt(bytes, at); at += 4
    val floorSteps = getInt(bytes, at); at += 4

    val height = FloatArray(samples)
    val water = FloatArray(samples)
    val block = ByteArray(samples)
    val canopy = ByteArray(samples)

    for (i in 0 until samples) { height[i] = metres(getShort(bytes, at), floorSteps); at += 2 }
    for (i in 0 until samples) {
      val raw = getShort(bytes, at); at += 2
      water[i] = if (raw == DRY) SurfacePatch.NO_WATER else metres(raw - 1, floorSteps)
    }
    for (i in 0 until samples) block[i] = bytes[at++]
    for (i in 0 until samples) canopy[i] = bytes[at++]

    return SurfacePatch(PatchPos(level, x, y), height, water, block, canopy)
  }

  /** How many bytes a payload is, before compression. Fixed, so a caller can size a buffer or a budget. */
  fun encodedSize() = HEADER_BYTES + PatchGrid.SAMPLES * PatchGrid.SAMPLES * BYTES_PER_SAMPLE

  private const val HEADER_BYTES = 1 + 1 + 4 + 4 + 4

  /** Two for height, two for water, one for the block id, one for canopy. */
  private const val BYTES_PER_SAMPLE = 6

  /**
   * A height as steps above the patch floor, clamped into the sixteen bits it has.
   *
   * The clamp cannot fire on a real world - two kilometres of relief inside one patch is more than the
   * generator can produce - but a payload that silently wrapped would draw a mountain as a pit, and that is
   * the kind of failure that gets diagnosed as a shader bug.
   */
  private fun steps(metres: Float, floorSteps: Int): Int =
    ((metres / HEIGHT_STEP).roundToInt() - floorSteps).coerceIn(0, 0xFFFE)

  private fun metres(steps: Int, floorSteps: Int) = ((steps + floorSteps) * HEIGHT_STEP).toFloat()

  private fun putShort(out: ByteArray, at: Int, value: Int): Int {
    out[at] = (value ushr 8).toByte()
    out[at + 1] = value.toByte()
    return at + 2
  }

  private fun getShort(bytes: ByteArray, at: Int) =
    ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)

  private fun putInt(out: ByteArray, at: Int, value: Int): Int {
    out[at] = (value ushr 24).toByte()
    out[at + 1] = (value ushr 16).toByte()
    out[at + 2] = (value ushr 8).toByte()
    out[at + 3] = value.toByte()
    return at + 4
  }

  private fun getInt(bytes: ByteArray, at: Int) =
    ((bytes[at].toInt() and 0xFF) shl 24) or ((bytes[at + 1].toInt() and 0xFF) shl 16) or
        ((bytes[at + 2].toInt() and 0xFF) shl 8) or (bytes[at + 3].toInt() and 0xFF)
}
