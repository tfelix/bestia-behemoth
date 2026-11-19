package net.bestia.zone.chronicle

import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.EventKind

/** The history of the world got a new entry. Published inside the writing transaction. */
data class WorldFactRecordedEvent(
  val factId: Long,
  val kind: EventKind,
  val actors: List<Actor>,
  /** The master who did it, when a master did. */
  val by: LivingChronicle.Doer? = null,
)
