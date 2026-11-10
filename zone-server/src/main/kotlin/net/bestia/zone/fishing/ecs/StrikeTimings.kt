package net.bestia.zone.fishing.ecs

import net.bestia.zone.ecs.core.Component

/** A master's last strike reaction times, in milliseconds, for [net.bestia.zone.fishing.StrikeTimingCheck]. */
class StrikeTimings : Component {

  private val samples = ArrayDeque<Long>()

  fun record(reactionMillis: Long, keep: Int) {
    samples.addLast(reactionMillis)
    while (samples.size > keep) {
      samples.removeFirst()
    }
  }

  fun samples(): List<Long> {
    return samples.toList()
  }

  fun clear() {
    samples.clear()
  }
}
