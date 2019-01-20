package net.bestia.model.map

typealias WalkspeedInt = Int
typealias WalkspeedFloat = Float

/**
 * Since the walkpeed in bestia can have different meanings and representations
 * (one encoded as float, the other as an int for transmission reason) the
 * walkspeed are completly encapsulated as a own class.
 *
 * @author Thomas Felix
 */
data class Walkspeed(
    private val speed: WalkspeedFloat
) {

  init {
    if (speed < 0 || speed > MAX_WALKSPEED) {
      throw IllegalArgumentException("Walkspeed in float form must be between 0 and 3.5f")
    }
  }

  /**
   * Returns the walkspeed as float.
   *
   * @return The current walkspeed as int.
   */
  fun toInt(): WalkspeedInt {
    return (speed * 100).toInt()
  }

  companion object {
    const val MAX_WALKSPEED = 3.5f
    const val MAX_WALKSPEED_INT = (MAX_WALKSPEED * 100).toInt()

    val ZERO = Walkspeed(0f)

    /**
     * Generates the walkspeed from an integer value. Value must be between 0
     * and [.MAX_WALKSPEED_INT].
     *
     * @param speed
     * The speed.
     * @return A walkspeed object.
     */
    fun fromInt(speed: WalkspeedInt): Walkspeed {
      if (speed < 0 || speed > MAX_WALKSPEED_INT) {
        throw IllegalArgumentException("Walkspeed in int form must be between 0 and 3500")
      }

      return Walkspeed(speed / 100f)
    }
  }
}
