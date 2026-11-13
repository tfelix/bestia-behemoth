package net.bestia.worldgen.core

/** A cult a prophet founded, where it spread, and what became of it. */
data class CultRecord(
  val index: Int,
  val nameSeed: Long,
  val kind: CultKind,
  /** Index into [Chronicle.deities] for a [CultKind.DEITY] cult, else -1. */
  val deity: Int,
  /** The sacred kind for a [CultKind.BEAST] cult, else null. */
  val beast: BeastKind?,
  /** Figure index of the prophet who founded it. */
  val founder: Int,
  val origin: Int,
  val foundedYear: Int,
  /** Year it ended, or 0 while it is kept. */
  val endedYear: Int,
  val fate: CultFate,
  /** Every settlement it ever spread to, the origin first. */
  val settlements: List<Int>,
  /** The Order its teaching leans toward, or null. */
  val leaning: Faction?,
  /** Site index of its sanctuary. */
  val sanctuary: Int,
  /** Civ index of the realm it founded, for [CultFate.REALM], else -1. */
  val realm: Int
)
