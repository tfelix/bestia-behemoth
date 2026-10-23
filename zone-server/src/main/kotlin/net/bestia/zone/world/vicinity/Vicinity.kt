package net.bestia.zone.world.vicinity

import net.bestia.zone.ecs.core.Component

/**
 * The entity senses what lies in the chunk columns within [radiusChunks] of its own: one gives the three by
 * three around it. See [VicinitySense] for what it is told.
 */
class Vicinity(val radiusChunks: Int = 1) : Component {
  init {
    require(radiusChunks >= 0) { "A vicinity radius cannot be negative" }
  }
}
