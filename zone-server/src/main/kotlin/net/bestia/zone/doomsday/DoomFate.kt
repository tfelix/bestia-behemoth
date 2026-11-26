package net.bestia.zone.doomsday

/** What a threat knows of its world's fate when it acts: where it began, in metres, and how far its clock has come. */
data class DoomFate(
  val originX: Double,
  val originY: Double,
  val progress: Double,
  val stage: DoomStage,
)
