package net.bestia.zone.cartography.poi

import net.bestia.zone.ai.core.state.HourWindow

/**
 * Which trades somebody keeps, and the hours they keep them.
 *
 * Declared here and implemented by the townsfolk, which sit above the map in the slice tiers: the map needs
 * to know that a baker stands behind the counter from four until two, and nothing else about who they are.
 */
interface StaffedTrades {

  /** Every staffed `BusinessType.id`, to its keeper's shift - null for a keeper with no shift of their own. */
  fun hoursByTrade(): Map<String, HourWindow?>
}
