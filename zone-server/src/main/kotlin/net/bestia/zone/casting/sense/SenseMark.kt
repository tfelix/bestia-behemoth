package net.bestia.zone.casting.sense

/** One thing a sensing skill found, at a spot on the map in metres. See `sense_marks_smsg.proto`. */
data class SenseMark(
  val kind: SenseKind,
  val eastMetres: Double,
  val northMetres: Double,
  val label: String = "",
  val refId: Long = 0,
  val depthMetres: Double = 0.0,
  val sizeMetres: Double = 0.0,
  /** True when the sense only says that something is near, not where. */
  val vague: Boolean = false,
)

enum class SenseKind { ORE, HERBS, TIMBER, GAME, HOLLOW, MANA, BINDING_FIRED }
