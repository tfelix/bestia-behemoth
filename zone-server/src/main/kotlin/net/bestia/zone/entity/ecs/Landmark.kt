package net.bestia.zone.entity.ecs

import net.bestia.worldgen.poi.PoiKind
import net.bestia.zone.ecs.core.Component

/**
 * A landmark the world generator placed: which one, and which of its rolled copies.
 *
 * (kind, copy) names the same landmark across restarts, while the entity id is new on every boot. Server-only.
 */
data class Landmark(
  val kind: PoiKind,
  val copy: Int
) : Component
