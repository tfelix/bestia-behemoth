package net.bestia.worldgen.core

/** A family that ruled a civ. Its members are the figures whose `dynasty` names it. */
data class DynastyRecord(
  val index: Int,
  val nameSeed: Long,
  /** Figure index of the ruler who founded it. */
  val founder: Int,
  val civ: Int,
  /** Settlement index it ruled from when it was founded. */
  val seat: Int,
  val foundedYear: Int,
  /** Year it lost the throne for good, or 0 while it rules. */
  val endedYear: Int,
  /** Civ index of the people it founded in exile, or -1. */
  val exiledTo: Int
)
