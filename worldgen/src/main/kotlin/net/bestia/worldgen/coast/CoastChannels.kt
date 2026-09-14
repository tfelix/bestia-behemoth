package net.bestia.worldgen.coast

/**
 * The station channels a [net.bestia.worldgen.vector.FeatureKind.COASTLINE] carries.
 *
 * Named here rather than spelled at each call site for `hydro.LakeChannels`' reason: a channel is looked up by
 * string, so a typo is a runtime failure in whichever chunk happened to straddle that feature.
 */
object CoastChannels {

  /**
   * [ShoreKind.ordinal] as a `Double`.
   *
   * **Read it with `StationTable.valueAt` and never `sample`.** Every other enum-in-a-channel in this module
   * lives on a single-station table, where `attribute` reads station zero and the question cannot arise. This
   * is the first multi-station table to carry a category, so it is the first place the Catmull-Rom in `sample`
   * would interpolate *between* two kinds and hand back a third - a stretch of cliff meeting a stretch of marsh
   * and coming out as the rocky shore that happens to sit between their ordinals. That is also why
   * `MarkerFeature.attributeAt` must not be used for this channel: it samples.
   */
  const val SHORE_KIND = "shore_kind"

  /** How far the strand reaches inland, in metres. Zero on a cliff. */
  const val BEACH_WIDTH = "beach_width"

  /** How high the berm crest stands above the water, in metres. */
  const val BERM_HEIGHT = "berm_height"

  /** The landward gradient measured at this station, dimensionless. */
  const val SHORE_SLOPE = "shore_slope"

  /** How open to the waves this stretch is, 0 to 1. */
  const val WAVE_EXPOSURE = "wave_exposure"

  /** How much sediment arrives here, 0 to 1. */
  const val SEDIMENT_SUPPLY = "sediment_supply"

  /**
   * Where this segment's claim on its loop begins and ends, as arc length along the segment in metres.
   *
   * Segments overlap geometrically by a few stations, so a column near a junction still projects into some
   * segment's *interior* rather than onto a clamped end. The claims do not overlap: they are half-open and tile
   * the loop exactly once, which is what makes one and only one segment answer for any column - with no gap
   * between two segments and no column counted twice where they lap.
   */
  const val CLAIM_START = "claim_start"
  const val CLAIM_END = "claim_end"
}
