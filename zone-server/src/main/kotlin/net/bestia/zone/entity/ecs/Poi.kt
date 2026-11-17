package net.bestia.zone.entity.ecs

import net.bestia.worldgen.poi.PoiKind
import net.bestia.zone.ecs.core.Component

/**
 * A POI the world generator placed: which kind, and which of its rolls.
 *
 * (kind, instanceId) names the same POI across restarts, while the entity id is new on every boot. Server-only.
 */
data class Poi(
  val kind: PoiKind,
  val instanceId: Int
) : Component
