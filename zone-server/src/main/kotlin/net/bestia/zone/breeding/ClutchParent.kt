package net.bestia.zone.breeding

import net.bestia.zone.battle.status.IndividualValues

/** A parent as the clutch roll sees it. [attacks] holds every attack it has learned, by skill id, at its learn level. */
class ClutchParent(
  val speciesId: Long,
  val level: Int,
  val ivs: IndividualValues,
  val attacks: Map<Long, Int>,
)
