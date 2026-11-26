package net.bestia.zone.doomsday

/**
 * How strongly the doomsday is felt at a place, from 0 to 1. It is strongest at its origin and reaches further as its
 * clock runs: in the Omens only those close by feel it, in the Cataclysm the whole world does.
 */
object DoomPresence {

  /** At [distance] metres from the origin of a doomsday at [stage] with its clock at [progress]. */
  fun at(distance: Double, stage: DoomStage, progress: Double): Double {
    val reach = REACH_MIN_METRES + (REACH_MAX_METRES - REACH_MIN_METRES) * progress.coerceIn(0.0, 1.0)
    val nearness = (1.0 - distance / reach).coerceIn(0.0, 1.0)
    val felt = Felt.of(stage)

    return maxOf(felt.atOrigin * nearness, felt.everywhere)
  }

  /** How strong it is at its origin, and how strong it is felt anywhere at all. */
  private enum class Felt(val atOrigin: Double, val everywhere: Double) {
    OMENS(0.3, 0.0),
    ONSET(0.6, 0.0),
    ESCALATION(0.85, 0.1),
    CATACLYSM(1.0, 0.45);

    companion object {
      fun of(stage: DoomStage): Felt {
        return valueOf(stage.name)
      }
    }
  }

  private const val REACH_MIN_METRES = 3_000.0
  private const val REACH_MAX_METRES = 60_000.0
}
