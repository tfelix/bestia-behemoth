package net.bestia.zone.ai.perception

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/**
 * An immutable snapshot of a single neighbour observed during a perception sweep. Taken under a
 * short foreign read lock so the think/act stages can reason about neighbours without holding any
 * foreign locks on the hot path.
 */
data class Percept(
  val entityId: EntityId,
  val position: Vec3L,
  val hostile: Boolean,
  val healthPct: Double
)
