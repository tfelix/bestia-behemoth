package net.bestia.zone.prop.station

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.geometry.Vec3L

/**
 * A bestia works the queue of the station [structureId] at [position]. Its AI keeps it there: home is the station
 * for as long as this is on, also after the player had control of it.
 */
class WorkAssignment(
  val structureId: Long,
  val position: Vec3L,

  /** The wander radius it had before, for when the work ends. */
  val formerWanderRadius: Long
) : Component
