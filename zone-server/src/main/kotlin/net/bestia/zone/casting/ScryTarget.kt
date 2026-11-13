package net.bestia.zone.casting

import net.bestia.zone.geometry.Vec3L

/** What a Scry looks at. */
sealed interface ScryTarget {

  /** A spot on the map, which must be charted. */
  data class Spot(val at: Vec3L) : ScryTarget

  /** Where a party member stands when the cast ends. The bond leads the sight, so no chart is needed. */
  data class PartyMember(val masterId: Long) : ScryTarget
}
