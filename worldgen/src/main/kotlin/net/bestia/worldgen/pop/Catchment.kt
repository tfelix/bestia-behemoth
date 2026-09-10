package net.bestia.worldgen.pop

import net.bestia.worldgen.civ.SettlementTier

/**
 * The ground a settlement draws on, and how its claim on it thins with distance.
 *
 * Public because the runtime has to reproduce it exactly. Working out whether a burnt field belonged to
 * a village means asking the same question the generator asked when it decided how much food that
 * village had - and two catchments that disagree would hurt one town for free and leave the other's
 * books short.
 */
object Catchment {

  /**
   * How much of its weight a cell loses at the very edge of a catchment. Never all of it.
   *
   * Linear rather than inverse-square, because at these distances what decides is how far a cart goes
   * in a morning, not a gravitational analogy.
   */
  const val CLAIM_FALLOFF = 0.75

  /**
   * Mean weight over the whole disc, which is what turns a radius into an area actually worked.
   *
   * The integral of `1 - (d/r)·f` over a disc, which is `1 - 2f/3` - a factor of two thirds rather than
   * a half, because there is more land near the rim than near the middle.
   */
  const val MEAN_CLAIM_WEIGHT = 1.0 - CLAIM_FALLOFF * 2.0 / 3.0

  fun radiusOf(tier: SettlementTier, params: EconomyParams): Double = when (tier) {
    SettlementTier.CITY -> params.cityCatchment
    SettlementTier.TOWN -> params.townCatchment
    SettlementTier.VILLAGE -> params.villageCatchment
    SettlementTier.HAMLET -> params.hamletCatchment
  }

  /**
   * A cell's share of one settlement, before it is divided by every claim on it.
   *
   * Catchments overlap, and this weight is what the sharing out divides by - so reproducing it is what
   * makes a contested field's two halves add up to one field rather than to two.
   */
  fun weightAt(distance: Double, radius: Double): Double {
    return 1.0 - distance / radius * CLAIM_FALLOFF
  }
}
