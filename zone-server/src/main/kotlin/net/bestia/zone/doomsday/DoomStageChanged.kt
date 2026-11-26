package net.bestia.zone.doomsday

/** The doomsday of [kind], at [originX], [originY] in metres, moved from stage [from] to [to]. */
data class DoomStageChanged(
  val kind: DoomKind,
  val originX: Double,
  val originY: Double,
  val from: DoomStage,
  val to: DoomStage,
)
