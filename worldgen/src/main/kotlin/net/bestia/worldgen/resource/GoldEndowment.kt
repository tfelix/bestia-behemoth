package net.bestia.worldgen.resource

import kotlin.math.max

/**
 * How much gold a world of a given size holds.
 *
 * What sizes a new world against the population it is meant to carry: a currency backed by the gold in
 * the ground is only as large as the ground, so "how many players" and "how many square kilometres" turn
 * out to be the same question. The caller searches this for an area rather than inverting it, because the
 * deposit floor makes it flat over every small world and a closed form would have to pretend otherwise.
 *
 * An estimate, not a simulation. [ResourceStage] places deposits by thinned Poisson sampling and traces
 * placer bars down real rivers, neither of which is a closed form. Modelled here are the two terms that
 * decide the total: the abundance, and the floor that overrules it.
 */
object GoldEndowment {

  /** Lode and placer together, in metric tons, for a world of [areaSqMetres]. */
  fun tonsIn(areaSqMetres: Double): Double {
    return lodeTonsIn(areaSqMetres) * (1.0 + PLACER_SHARE_OF_LODE)
  }

  /**
   * The lode total: an abundance, except where the guaranteed deposits overrule it.
   *
   * Below roughly 375 km the floor is what a world actually gets. Three deposits that may not be drawn
   * empty hold more than 0.49 t per thousand square kilometres asks for, so a small world is richer per
   * square kilometre than a large one and cannot be sized from its area alone.
   */
  fun lodeTonsIn(areaSqMetres: Double): Double {
    return max(guaranteedTons(), MinableOre.GOLD.worldTons(areaSqMetres))
  }

  /** What the deposit floor alone puts in the ground, whatever the abundance says. */
  fun guaranteedTons(): Double {
    return MinableOre.GOLD.guaranteedDeposits * ResourceStage.MIN_DEPOSIT_TONS
  }

  /** Ore voxels a player has to break to recover [tons] of metal. */
  fun voxelsForTons(tons: Double, meanYieldKg: Double): Double {
    return OreBody.voxelsInTons(tons, meanYieldKg)
  }

  /**
   * Placer gold as a share of the lode total.
   *
   * `tracePlacers` walks downstream of every lode dropping a bar every 12 km out to 90 km, decaying
   * exponentially - about 7.6 t per lode where the river runs the whole way, which it seldom does, so
   * half of that is the working figure. Both terms scale with the number of lodes, which is why the
   * share holds far steadier across world sizes than either number does alone.
   */
  const val PLACER_SHARE_OF_LODE = 0.45
}
