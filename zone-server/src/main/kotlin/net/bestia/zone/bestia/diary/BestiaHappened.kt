package net.bestia.zone.bestia.diary

import net.bestia.zone.BestiaEvent
import net.bestia.zone.util.PlayerBestiaId

/**
 * Something an owned bestia did or met that its master reads in the diary later, and some of which they are told at
 * once. Published on the tick: a listener must not block.
 *
 * [items] are item identifiers with amounts, [place] what it happened at (a station's kind), [reason] why work
 * stopped.
 */
class BestiaHappened(
  source: Any,
  val masterId: Long,
  val playerBestiaId: PlayerBestiaId,
  val kind: Kind,
  val items: Map<String, Int> = emptyMap(),
  val place: String? = null,
  val reason: String? = null,
) : BestiaEvent(source) {

  enum class Kind {
    NEW_ORDERS,
    HAULED,

    /** Work stopped and will not go on by itself: the fire is out, the chest is full, the shaft is dug out. */
    WORK_PAUSED,
    QUEUE_FINISHED,

    /** Hit after a quiet while. */
    ATTACKED,
    RETREATED,
    DIED,

    /** Overkilled: it is gone for good. */
    LOST,
    /** Below its eat line with no food in its bag or its chest: it waits at home until somebody brings some. */
    OUT_OF_FOOD,

    /** The food it may eat lasts about two more hours of work. */
    LOW_FOOD,

    /** Keeping watch at an Observation Outpost, it felt a world event begin to stir; [reason] is its kind. */
    EVENT_SENSED,
  }
}
