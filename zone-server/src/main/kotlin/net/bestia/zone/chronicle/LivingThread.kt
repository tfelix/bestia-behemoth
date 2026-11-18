package net.bestia.zone.chronicle

import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ThreadKind

/**
 * A story that is open today. It stayed open since an event of the generated chronicle, or it opened in play
 * with a world fact; exactly one of the two is set.
 */
data class LivingThread(
  val kind: ThreadKind,
  val actors: List<Actor>,
  val sinceEvent: Int? = null,
  val sinceFact: Long? = null,
)
