package net.bestia.model.map

/**
 * Since the walkpeed in bestia can have different meanings and representations
 * (one encoded as float, the other as an int for transmission reason) the
 * walkspeed are completly encapsulated as a own class.
 *
 * @author Thomas Felix
 */
data class Walkspeed(
    private var _speed: Float = 1f
) {

  /**
   * Gets the current speed.
   *
   * @return The current walkspeed.
   */
  var speed: Float = 0f
    set(value) {
      if (value < 0 || value > MAX_WALKSPEED) {
        throw IllegalArgumentException("Walkspeed in float form must be between 0 and 3.5f")
      }
      field = value
    }
    get() = _speed

  /**
   * Returns the walkspeed as float.
   *
   * @return The current walkspeed as int.
   */
  fun toInt(): Int {
    return (speed * 100).toInt()
  }

  companion object {
    const val MAX_WALKSPEED = 3.5f
    const val MAX_WALKSPEED_INT = (MAX_WALKSPEED * 100).toInt()

    val ZERO
      get() = Walkspeed()

    /**
     * Generates the walkspeed from an integer value. Value must be between 0
     * and [.MAX_WALKSPEED_INT].
     *
     * @param speed
     * The speed.
     * @return A walkspeed object.
     */
    fun fromInt(speed: Int): Walkspeed {
      if (speed < 0 || speed > MAX_WALKSPEED_INT) {
        throw IllegalArgumentException("Walkspeed in int form must be between 0 and 3500")
      }

      return Walkspeed().apply { this.speed = speed / 100f }
    }
  }
}
