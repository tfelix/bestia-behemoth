package net.bestia.zone.prop

import net.bestia.zone.BestiaEvent
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L

/** A player structure or its site is gone: knocked down, or broken by decay. Published on the tick. */
class PlayerStructureDestroyedEvent(
  source: Any,
  val structureId: Long,
  val kind: StaticEntityKind,
  val position: Vec3L,
) : BestiaEvent(source)
