package net.bestia.zone.world.anchor

import net.bestia.zone.ecs.core.Component

/**
 * Simulate the world around this entity as if a player stood here: resident walk data, dens, wildlife and
 * full-rate AI. Every master and player bestia in the world carries one.
 */
class SimulationAnchor(
  /** Raised while the entity travels, so a capped server keeps simulating the ones on the move. */
  var priority: AnchorPriority = AnchorPriority.RESTING
) : Component
