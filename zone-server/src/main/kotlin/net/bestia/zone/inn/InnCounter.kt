package net.bestia.zone.inn

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/** The inn a request is made at: who serves there, where its door is, and which settlement it belongs to. */
data class InnCounter(
  val entityId: EntityId,
  val door: Vec3L,
  val settlementName: String,
)
