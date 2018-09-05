package net.bestia.zoneserver.battle

/**
 * Different weapons deal different damage towards different sized bestias.
 *
 * @author Thomas Felix
 */
object SizeModifier {

  private val sizeMap = mutableMapOf<SizeKey, Float>()

  /**
   * Wraps the creation of size keys to retrieve the size modifier.
   *
   */
  private data class SizeKey(
          val s1: Size,
          val s2: Size
  )

  init {
    sizeMap[SizeKey(Size.SMALL, Size.SMALL)] = 1.3f
    sizeMap[SizeKey(Size.MEDIUM, Size.SMALL)] = 1f
    sizeMap[SizeKey(Size.BIG, Size.SMALL)] = 0.75f

    sizeMap[SizeKey(Size.SMALL, Size.MEDIUM)] = 1f
    sizeMap[SizeKey(Size.MEDIUM, Size.MEDIUM)] = 1.15f
    sizeMap[SizeKey(Size.BIG, Size.MEDIUM)] = 1f

    sizeMap[SizeKey(Size.SMALL, Size.BIG)] = 0.7f
    sizeMap[SizeKey(Size.MEDIUM, Size.BIG)] = 1.1f
    sizeMap[SizeKey(Size.BIG, Size.BIG)] = 1.3f
  }

  /**
   * Returns the damage value modifier as a float value (e.g. 1.25).
   *
   * @param attacker Size of the attacker.
   * @param defender Size of the defender.
   * @return The damage modifier.
   */
  fun getModifier(attacker: Size, defender: Size): Float {
    val sk = SizeKey(attacker, defender)
    return sizeMap[sk] ?: 1.0f
  }
}
