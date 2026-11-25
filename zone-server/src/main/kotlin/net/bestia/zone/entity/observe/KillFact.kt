package net.bestia.zone.entity.observe

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/** A wild bestia died. [creditedMasters] are the master entities that earned EXP from it, party shares included. */
data class KillFact(
  val speciesId: Long,
  val position: Vec3L,
  val creditedMasters: Set<EntityId>,
  val level: Int,
  val boss: Boolean,
)
