package net.bestia.zone.ai.domain.bestia

import net.bestia.zone.geometry.Vec3L

/**
 * A place a master named on the map for their bestias' orders: a work area, or one of their chests. Many bestias can
 * use the same place, and moving it moves it for all of them.
 */
data class NamedPlace(
  val id: Long,
  val masterId: Long,
  val name: String,
  val kind: Kind,
  /** The area's middle, or the chest's spot. */
  val center: Vec3L,
  /** The area's radius in tiles; 0 for a chest. */
  val radius: Long,
  /** The chest structure a chest place names. */
  val chestStructureId: Long?,
) {
  enum class Kind { AREA, CHEST }

  val area: WorkArea
    get() = WorkArea(center, radius)
}

/** Places by id, for the senses. The master slice keeps them; it owns the orders that name them. */
fun interface PlaceLookup {
  fun placeOf(id: Long): NamedPlace?
}
