package net.bestia.zone.ecs.item

import net.bestia.zone.ecs.core.Component
import java.time.Instant

/**
 * When a dropped plain item is gone. Persisted with the item, so a restart does not give it its full time again.
 */
class GroundItemDecay(
  val despawnAt: Instant
) : Component
