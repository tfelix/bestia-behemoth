package net.bestia.zone.entity.observe

import net.bestia.zone.util.EntityId

/** [actorId], a master or one of its bestias, did [deed] to [target]. */
data class DeedFact(
  val actorId: EntityId,
  val deed: Deed,
  val target: WorldTarget,
) {

  enum class Deed {
    /** Read it or looked closely at it. */
    EXAMINED,

    /** Walked up to the person and spoke to them. */
    TALKED_TO,
  }
}
