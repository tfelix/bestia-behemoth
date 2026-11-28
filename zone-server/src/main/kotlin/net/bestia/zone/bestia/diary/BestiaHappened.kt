package net.bestia.zone.bestia.diary

import net.bestia.zone.BestiaEvent
import net.bestia.zone.util.PlayerBestiaId
import java.time.Duration

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

  /** A kind with [mergesWithin] adds its items to the bestia's last line of that kind and place for that long. */
  enum class Kind(val mergesWithin: Duration? = null) {
    NEW_ORDERS,
    HAULED(mergesWithin = Duration.ofHours(1)),

    /** Work stopped and will not go on by itself: the fire is out, the chest is full, the shaft is dug out. */
    WORK_PAUSED,
    QUEUE_FINISHED,

    /** Hit after a quiet while. */
    ATTACKED,
    RETREATED,

    /** Joined a fight its master or another of its master's bestias was in, after a quiet while. */
    HELPED,
    DIED,

    /** Overkilled: it is gone for good. */
    LOST,
    /** Below its eat line with no food in its bag or its chest: it waits at home until somebody brings some. */
    OUT_OF_FOOD,

    /** The food it may eat lasts about two more hours of work. */
    LOW_FOOD,

    /** Keeping watch at an Observation Outpost, it felt a world event begin to stir; [reason] is its kind. */
    EVENT_SENSED,

    /** What it does has changed for a while; [reason] is the new [BestiaState]. For the away report, not the diary. */
    STATE,

    /** Its gather area holds nothing more it may take. */
    FIELD_EMPTY,

    /** Its main job ran out of work and its fallback job took over; [reason] is the fallback job. */
    FALLBACK_STARTED,
  }
}
