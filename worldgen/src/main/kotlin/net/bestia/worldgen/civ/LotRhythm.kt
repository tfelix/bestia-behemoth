package net.bestia.worldgen.civ

/**
 * How evenly a street's plots are laid out along it.
 *
 * A chartered town's frontage was surveyed and a village's grew, and on the ground that difference is these
 * three numbers rather than anything about the streets: even spacing against ragged gaps, one building line
 * against houses standing back by as much as their owners liked, and every gable square to the road against
 * none of them quite square.
 *
 * Every jitter is one-sided or symmetric in the direction that cannot cause a defect: a gap is only ever added
 * between plots, and a setback is only ever increased, so no roll can put a building on its neighbour or in
 * the carriageway.
 */
internal class LotRhythm(
  /** Extra gap between neighbouring plots, as a fraction of a frontage, rolled in `[0, this]`. */
  val spacingJitter: Double,
  /** Extra distance back from the kerb, as a fraction of the setback, rolled in `[0, this]`. */
  val setbackJitter: Double,
  /** How far a building may stand off square to its street, in radians, rolled in `[-this, this]`. */
  val bearingJitter: Double
) {

  init {
    require(spacingJitter >= 0.0) { "spacingJitter must not be negative, was $spacingJitter" }
    require(setbackJitter >= 0.0) { "setbackJitter must not be negative, was $setbackJitter" }
    require(bearingJitter >= 0.0) { "bearingJitter must not be negative, was $bearingJitter" }
  }

  companion object {

    /** A surveyed frontage: plots on an exact lattice, one building line, every gable square. */
    val SURVEYED = LotRhythm(0.0, 0.0, 0.0)

    /**
     * A village that grew along its road.
     *
     * The bearing figure is about seven degrees, which is plainly not square at a glance down the street and
     * still reads as a row rather than as a scatter.
     */
    val RURAL = LotRhythm(0.55, 0.35, 0.12)
  }
}
