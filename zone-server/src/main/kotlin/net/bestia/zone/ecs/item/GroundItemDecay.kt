package net.bestia.zone.ecs.item

import net.bestia.zone.ecs.core.Component

/**
 * How much longer a dropped plain item lies on the ground. Server-only and not persisted: a restart gives every
 * item on the ground its full time again, which only delays the cleanup.
 */
class GroundItemDecay(
  var remainingSeconds: Float
) : Component
