package net.bestia.zone.quest.ecs

import net.bestia.zone.entity.observe.DeedFact
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.fact.FactRef
import java.time.Instant

/** One personal objective a played master meets by a deed to a target. The log entry is told every new [count]. */
class TrackedDeed(
  val questId: Long,
  val objectiveIndex: Int,
  val deed: DeedFact.Deed,
  val target: WorldTarget,
  /** What the master must know for the deed to count, such as the name it is to tell. */
  val needsFact: FactRef?,
  val required: Int,
  val deadline: Instant,
  var count: Int,
) {

  fun counts(now: Instant, done: DeedFact.Deed): Boolean {
    return done == deed && count < required && !now.isAfter(deadline)
  }
}
