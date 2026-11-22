package net.bestia.zone.casting

/**
 * How long a cast bar runs: WIL shortens it (`1 - WIL/150`, the docs' "willpower reduces cast duration"), gear and
 * buffs take their percentage off that, and a fifth always remains, so nothing casts instantly.
 */
object CastTime {

  fun seconds(baseSeconds: Float, willpower: Int, reductionPercent: Int): Float {
    val byWill = 1f - willpower.coerceAtLeast(0) / WILL_FOR_INSTANT
    val byGear = 1f - reductionPercent / 100f

    return baseSeconds * (byWill * byGear).coerceAtLeast(MIN_SHARE)
  }

  /** The WIL that would make a cast instant, were it not for [MIN_SHARE]. */
  private const val WILL_FOR_INSTANT = 150f

  const val MIN_SHARE = 0.2f
}
