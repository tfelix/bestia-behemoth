package net.bestia.zone.trade

import net.bestia.zone.util.PlayerBestiaId

/**
 * Whether a bestia is paired in a breeding farm, which keeps it out of a trade. The breeding slice sits above
 * trade, so it implements this port rather than trade reaching up to it.
 */
fun interface PairedBestias {
  fun isPaired(playerBestiaId: PlayerBestiaId): Boolean
}
