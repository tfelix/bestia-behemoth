package net.bestia.zone.movement.ecs

import net.bestia.zone.ecs.core.Component

/**
 * Moves the entity only every [everyTicks] ticks, by the time that passed since. For walkers nobody can see,
 * where a step per tick buys nothing.
 */
class CoarseMovement(val everyTicks: Long) : Component {

  /** The tick it last moved on, or -1 before the first. */
  var lastTick: Long = -1L
}
