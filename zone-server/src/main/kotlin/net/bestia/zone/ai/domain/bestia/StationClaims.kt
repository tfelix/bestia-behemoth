package net.bestia.zone.ai.domain.bestia

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** How a crafter takes a station that waits for one. The crafting slice implements it: it owns who works where. */
fun interface StationClaims {

  /** @return false when the station no longer waits for a crafter or has nothing to do */
  fun claim(world: World, bestiaId: EntityId, structureId: Long): Boolean
}
