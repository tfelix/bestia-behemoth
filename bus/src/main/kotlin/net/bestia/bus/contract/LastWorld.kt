package net.bestia.bus.contract

/**
 * How the last world lived and ended, as its final sequence told it. [threat] is a `DoomKind` name, [ending] a
 * `DoomEnding` name; the bus does not depend on the zone. [lifeSpanPercent] is the world's age at its end, in percent
 * of the life span its doomsday gave it.
 */
data class LastWorld(
  val name: String,
  val threat: String?,
  val ending: String,
  val daysLived: Int,
  val lifeSpanPercent: Int,
  val townsLost: Int,
  val endedAtEpochMs: Long,
)
