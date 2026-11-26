package net.bestia.zone.doomsday

/** Everything a GM wants to know about the doomsday at a glance. Days are days of world age. */
data class DoomsdayStatus(
  val kind: DoomKind?,
  val originX: Double?,
  val originY: Double?,
  val stage: DoomStage,
  val progress: Double,
  val pace: Double,
  val paceOverridden: Boolean,
  val pressure: Double,
  val containment: Double,
  val ageDays: Double,
  val onsetDay: Double,
  val lifeSpanDays: Double,
  val tPercent: Double,
  val projectedEndDay: Double,
  val foreshadowKind: ForeshadowKind?,
  val foreshadowDay: Double,
  val ending: DoomEnding?,
)
