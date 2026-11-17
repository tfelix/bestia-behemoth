package net.bestia.zone.quest.ecs

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.place.ecs.PlaceRef
import net.bestia.zone.quest.QuestArea
import java.time.Instant

/** One personal kill objective a played master is counting. The log entry is told every new [count]. */
class TrackedKill(
  val questId: Long,
  val objectiveIndex: Int,
  val speciesId: Long,
  val area: QuestArea?,
  val required: Int,
  val deadline: Instant,
  var count: Int,
) {

  fun counts(now: Instant, killerPlace: PlaceRef?, killedAt: Vec3L): Boolean {
    if (count >= required || now.isAfter(deadline)) {
      return false
    }

    return area?.contains(killerPlace, killedAt) ?: true
  }
}
