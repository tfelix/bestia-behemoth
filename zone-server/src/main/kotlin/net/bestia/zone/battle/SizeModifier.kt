package net.bestia.zone.battle

/**
 * Different weapons deal different damage towards different sized bestias.
 *
 * @author Thomas Felix
 */
object SizeModifier {

  private val sizeMap = mutableMapOf<SizeKey, Int>()

  /**
   * Wraps the creation of size keys to retrieve the size modifier.
   *
   */
  private data class SizeKey(
    val s1: Size,
    val s2: Size
  )

  init {
    sizeMap[SizeKey(Size.SMALL, Size.SMALL)] = 100
    sizeMap[SizeKey(Size.MEDIUM, Size.SMALL)] = 120
    sizeMap[SizeKey(Size.BIG, Size.SMALL)] = 75

    sizeMap[SizeKey(Size.SMALL, Size.MEDIUM)] = 100
    sizeMap[SizeKey(Size.MEDIUM, Size.MEDIUM)] = 100
    sizeMap[SizeKey(Size.BIG, Size.MEDIUM)] = 100

    sizeMap[SizeKey(Size.SMALL, Size.BIG)] = 100
    sizeMap[SizeKey(Size.MEDIUM, Size.BIG)] = 100
    sizeMap[SizeKey(Size.BIG, Size.BIG)] = 100
  }

  /**
   * Returns the damage value modifier as a int value (e.g. 125).
   *
   * @param attacker Size of the attacker.
   * @param defender Size of the defender.
   * @return The damage modifier.
   */
  fun getModifier(attacker: Size, defender: Size): Int {
    val sk = SizeKey(attacker, defender)
    return sizeMap[sk] ?: 100
  }
}
