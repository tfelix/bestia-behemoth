package net.bestia.worldgen.core

/**
 * Counter-based deterministic RNG.
 *
 * There is no shared mutable global RNG anywhere in the pipeline, and there never may be: a shared
 * stream makes every result depend on the order things were drawn in, which destroys both
 * reproducibility and the ability to generate two chunks on two nodes in either order. Instead
 * every stream is *derived* by hashing its full coordinate:
 *
 * ```
 * rng = GenRng.derive(worldSeed, stageId, stageVersion, chunkX, chunkY, salt)
 * ```
 *
 * Two runs that derive the same coordinate get the same stream regardless of what else ran, in what
 * order, on how many threads.
 *
 * The mixing function is SplitMix64 - cheap, passes BigCrush, and has no weak seeds, so hashing a
 * structured key straight into it is safe.
 */
class GenRng(seed: Long) {

  private var state: Long = seed

  /** Next raw 64 bits. */
  fun nextLong(): Long {
    state += GOLDEN_GAMMA
    return mix64(state)
  }

  fun nextInt(): Int = (nextLong() ushr 32).toInt()

  /** Uniform in `[0, bound)`. */
  fun nextInt(bound: Int): Int {
    require(bound > 0) { "bound must be positive, was $bound" }
    // Lemire's multiply-shift; unbiased enough for content generation and branch-free.
    val product = (nextLong() ushr 33) * bound
    return (product ushr 31).toInt().coerceAtMost(bound - 1)
  }

  /** Uniform in `[0, 1)`. */
  fun nextDouble(): Double = (nextLong() ushr 11) * DOUBLE_UNIT

  /** Uniform in `[min, max)`. */
  fun nextDouble(min: Double, max: Double): Double = min + nextDouble() * (max - min)

  /** Standard normal, via Box-Muller. Two draws from the stream per call. */
  fun nextGaussian(): Double {
    // 1 - nextDouble() keeps the argument of ln strictly positive.
    val u1 = 1.0 - nextDouble()
    val u2 = nextDouble()
    return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
  }

  fun nextBoolean(): Boolean = nextLong() < 0L

  /**
   * A child stream for a sub-computation, derived from this stream's *current* position.
   *
   * Use it when a stage wants an independent stream per item; do not reuse the parent afterwards
   * for anything whose result must not depend on how many children were taken.
   */
  fun fork(salt: Long) = GenRng(mix64(nextLong() xor mix64(salt)))

  companion object {

    private const val GOLDEN_GAMMA = -0x61c8864680b583ebL // 0x9e3779b97f4a7c15
    private const val DOUBLE_UNIT = 1.0 / (1L shl 53)

    /**
     * Derives a stream from a structured key. Every component is folded in, so changing any one of
     * them - including [stageVersion] - yields a completely unrelated stream. That is what makes a
     * stage version bump a genuine cache invalidation rather than a cosmetic one.
     */
    fun derive(worldSeed: Long, stageId: StageId, stageVersion: Int, vararg coordinates: Long): GenRng {
      var h = mix64(worldSeed)
      h = mix64(h xor stageId.hash)
      h = mix64(h xor stageVersion.toLong())
      for (c in coordinates) {
        h = mix64(h xor c)
      }
      return GenRng(h)
    }

    /**
     * A single deterministic value from a key, without materialising a stream.
     *
     * This is the right tool inside a per-column loop, where allocating a [GenRng] per column would
     * dominate the cost of the loop.
     */
    fun hash(vararg values: Long): Long {
      var h = mix64(values.size.toLong())
      for (v in values) {
        h = mix64(h xor v)
      }
      return h
    }

    /** [hash] mapped into `[0, 1)`. */
    fun hashUnit(vararg values: Long): Double = (hash(*values) ushr 11) * DOUBLE_UNIT

    /** Stable 64-bit hash of a string, for folding stage and channel names into keys. */
    fun hashString(value: String): Long {
      var h = -0x340d631b7bdddcdbL // FNV-1a 64 offset basis
      for (c in value) {
        h = h xor c.code.toLong()
        h *= 0x100000001b3L
      }
      return mix64(h)
    }

    fun mix64(value: Long): Long {
      var z = value
      z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xbf58476d1ce4e5b9
      z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94d049bb133111eb
      return z xor (z ushr 31)
    }
  }
}
