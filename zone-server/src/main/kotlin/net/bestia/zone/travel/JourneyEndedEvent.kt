package net.bestia.zone.travel

import net.bestia.zone.BestiaEvent
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/**
 * A journey ended: arrived when [failure] is null. Published on the tick, so a listener must not block or
 * touch the world.
 */
class JourneyEndedEvent(
  source: Any,
  val entityId: EntityId,
  val order: JourneyOrder,
  val name: String?,
  val position: Vec3L,
  val failure: JourneyFailure?,
) : BestiaEvent(source)
