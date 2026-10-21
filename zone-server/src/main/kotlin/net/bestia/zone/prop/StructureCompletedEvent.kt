package net.bestia.zone.prop

import net.bestia.zone.BestiaEvent

/** A player's construction site became a finished structure. Published on the tick: a listener must not block. */
class StructureCompletedEvent(
  source: Any,
  val ownerAccountId: Long,
  val ownerMasterId: Long,
  val kind: String,
) : BestiaEvent(source)
