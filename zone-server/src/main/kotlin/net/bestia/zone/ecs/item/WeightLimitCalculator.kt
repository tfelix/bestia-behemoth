package net.bestia.zone.ecs.item

import org.springframework.stereotype.Component

/**
 * Pure calculations backing [CarryCapacity]. The limit is on [net.bestia.zone.item.Item.weight]'s scale of
 * 100 per kilogram, so the constants below are hundredths of a kilogram.
 *
 * The docs' formula is the same shape at a hundredth of this scale and is stale until it is rescaled too:
 * https://docs.bestia-game.net/docs/mechanics/items/#weight-limit
 */
@Component
class WeightLimitCalculator {

  fun computeWeightLimit(strength: Int, vitality: Int, level: Int): Int {
    return BASE + strength * PER_STRENGTH + vitality * PER_VITALITY + level * PER_LEVEL
  }

  companion object {
    /**
     * Anchored at both ends of a master's life: the default 10/10 attributes at level 1 give 2475 (~25kg,
     * enough for a set of gear and a day's supplies), and 100 strength at level 100 gives 9450-10800
     * (~95-108kg, a porter's load) depending on what the rest of the spread looks like. Strength carries
     * most of it because that is the attribute a player invests in to haul; level is a small floor so a
     * build that never touches strength or vitality still gains a little room as it grows.
     *
     * The old formula divided by these instead of multiplying, and integer division at the low end left a
     * fresh master with a 22 unit limit - less than a single lump of ore. Every anchor above is pinned by
     * `WeightLimitCalculatorTest`, and `ItemCatalogWeightTest` guards the "no item outweighs a fresh
     * master" property that the old one broke for a third of the catalogue.
     */
    private const val BASE = 1_800
    private const val PER_STRENGTH = 50
    private const val PER_VITALITY = 15
    private const val PER_LEVEL = 25
  }
}
