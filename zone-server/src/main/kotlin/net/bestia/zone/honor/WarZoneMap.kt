package net.bestia.zone.honor

import net.bestia.zone.geometry.Vec3L

/**
 * Where the Orders fight openly, see the War Zone on the PvP page. The faction slice keeps the zones; honor only
 * asks and reports, because it sits below that slice.
 */
interface WarZoneMap {

  fun covers(at: Vec3L): Boolean

  /** Members of opposing Orders fought to the death at [at]. Enough of these close together open a zone. */
  fun rivalKill(at: Vec3L)
}
