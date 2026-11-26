package net.bestia.zone.doomsday.foreshadow

import net.bestia.zone.doomsday.ForeshadowKind
import net.bestia.zone.doomsday.persistence.StoredForeshadowing

/**
 * A lesser threat that strikes once, in one province, months before the doomsday sets in. Players who contain it move
 * the onset later; if it runs its course, the onset comes sooner and the land keeps its scars.
 */
interface Foreshadowing {

  val kind: ForeshadowKind

  /** Acts on its province for [realSeconds], and counts in [season] what players held and what they lost. */
  fun step(season: StoredForeshadowing, realSeconds: Double)

  /** Whether the players held more of it than they lost. */
  fun contained(season: StoredForeshadowing): Boolean {
    return season.held >= season.lost
  }

  /** It ran its course. A Foreshadowing that was not [contained] leaves its scars. */
  fun end(season: StoredForeshadowing, contained: Boolean) {
  }
}
