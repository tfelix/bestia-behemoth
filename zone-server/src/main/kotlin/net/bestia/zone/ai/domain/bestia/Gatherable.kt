package net.bestia.zone.ai.domain.bestia

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/**
 * A prop a gatherer saw in its area. [entityId] is only good until the prop's column reloads, so whoever acts on
 * it checks it still names [propId].
 */
data class Gatherable(val entityId: EntityId, val propId: Long, val position: Vec3L, val work: Work) {

  enum class Work {
    /** Picked up whole, as a player clicks it. */
    COLLECT,

    /** Felled, and what it drops picked up. */
    FELL,
  }
}
