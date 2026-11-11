package net.bestia.zone.ai.domain.bestia

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** How a worker takes a station that waits for one, and puts it down. The crafting slice owns who works where. */
interface StationClaims {

  /** @return false when the station no longer waits for a worker or has nothing to do */
  fun claim(world: World, bestiaId: EntityId, structureId: Long): Boolean

  /** Takes [bestiaId] off the station it works, so it goes back to its area. */
  fun quit(world: World, bestiaId: EntityId)
}
