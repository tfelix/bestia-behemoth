package net.bestia.zone.world.mana

/**
 * What the local mana concentration does to magic: the Place term of a cast's Potency and the ManaFactor of a Mana
 * Circle. The worldgen field runs 0..1. The middle of it, 0.4 to 0.6, is ordinary and changes nothing. Below that it
 * falls to -20 at an empty field; above it climbs to +20 at a full one, and tapers towards +40 if a field ever
 * runs higher than that.
 */
object ManaConcentration {

  private const val ORDINARY_LOW = 0.4
  private const val ORDINARY_HIGH = 0.6
  private const val DRAINED_PLACE = -20.0
  private const val FULL_PLACE = 20.0
  private const val EXTREME_PLACE = 40.0

  /** Potency's Place term for a field of [mana]. */
  fun place(mana: Double): Int {
    val place = when {
      mana < ORDINARY_LOW -> DRAINED_PLACE * (ORDINARY_LOW - mana.coerceAtLeast(0.0)) / ORDINARY_LOW
      mana <= ORDINARY_HIGH -> 0.0
      mana <= 1.0 -> FULL_PLACE * (mana - ORDINARY_HIGH) / (1.0 - ORDINARY_HIGH)
      else -> EXTREME_PLACE - (EXTREME_PLACE - FULL_PLACE) / (1.0 + 2.0 * (mana - 1.0))
    }

    return place.toInt()
  }

  /** A Mana Circle's ManaFactor: 1 where mana is ordinary, 0.5 on empty ground, 1.5 on a full field. */
  fun factor(mana: Double): Double = 1.0 + place(mana) / (2.0 * FULL_PLACE)
}
