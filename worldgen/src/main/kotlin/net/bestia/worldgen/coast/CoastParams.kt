package net.bestia.worldgen.coast

import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.geo.DetailParams

/**
 * Tunables for [CoastStage].
 *
 * **Every threshold in the classifier below is a guess.** What is defensible is the *ordering* of the rules and
 * the *shape* of the relationships - a delta outranks a threshold because it is a fact about a feature; a berm
 * rises with exposure while its strand narrows, because a sheltered beach is wide and low and an exposed one is
 * narrow and steep. Keep the relationships when the numbers move.
 */
data class CoastParams(

  /**
   * Metres per cell of the sign field the shoreline is traced on.
   *
   * Far finer than the kilometre raster, and it has to be. The generator leaves a coastal shelf sitting within
   * about twenty metres of sea level for two to four kilometres, so the landward gradient at the shore is around
   * a hundredth - and the detail noise the chunk tier adds is a couple of metres of it. Tracing the coarse raster
   * would put the line hundreds of metres from where a chunk actually builds the waterline.
   */
  val cellSize: Double = 125.0,

  /** Metres of shore below which a traced loop is noise rather than an island. */
  val minPerimeter: Double = 1_500.0,

  /** Spacing of the stations along the traced line, in metres. */
  val stationSpacing: Double = 120.0,

  /** Stations per emitted segment. With [stationSpacing] this is how much shore one feature answers for. */
  val stationsPerSegment: Int = 48,

  /**
   * Metres per axis a segment may span.
   *
   * The other half of [stationsPerSegment], and the binding one on a straight coast. `FeatureIndex` buckets at
   * between one and four kilometres, so six keeps a segment inside a handful of cells - three orders of
   * magnitude clear of the per-feature cell cap that `AreaFeature`'s own eight-kilometre limit was measured
   * against.
   */
  val maxSegmentExtent: Double = 6_000.0,

  /**
   * The widest strand any station may claim, in metres.
   *
   * A hard bound rather than a tunable look: `ChunkMaterializer.MARKER_MARGIN` is how far outside its own
   * bounds a chunk queries, a marker's box is unexpanded, and a column further inland than that would ask a
   * chunk that never received the feature. Raising this above the margin puts holes in the beach.
   */
  val maxBeachWidth: Double = 300.0,

  /** Rays cast per shore cell when measuring fetch, and how many cells each may run before it counts as open. */
  val fetchRays: Int = 8,
  val fetchReach: Int = 60,

  /** Landward gradient below which a shore is flat enough to be marsh, given the rain and the shelter for it. */
  val marshSlope: Double = 0.010,

  /** Millimetres of annual rain a marsh needs. */
  val marshPrecipitation: Double = 700.0,

  /** Above this exposure a shore is too wave-beaten to hold marsh. */
  val marshExposure: Double = 0.25,

  /** Landward gradient above which a hard shore is a cliff rather than a rocky one. */
  val cliffSlope: Double = 0.35,

  /** Rock hardness a cliff needs, 0 to 1. Soft rock slumps into a shingle bank instead. */
  val cliffHardness: Double = 0.55,

  /** Above this exposure, and below [rockySediment] supply, the waves sweep a shore down to its rock. */
  val rockyExposure: Double = 0.55,
  val rockySediment: Double = 0.25,

  /** Sediment a sand beach needs, and the exposure above which even a well-fed shore comes out shingle. */
  val sandSediment: Double = 0.45,
  val sandExposure: Double = 0.75,

  /** Metres either side of a delta lobe that still count as its flat. */
  val deltaReach: Double = 250.0,

  /**
   * The detail the chunk tier adds to the heightfield.
   *
   * Forwarded from `WorldParams.resolved` rather than defaulted here, for the reason `pond` and `town` forward
   * theirs: this stage walks the surface a chunk will build, and two independently defaulted copies of that
   * surface agree right up until one of them is retuned.
   */
  val detail: DetailParams = DetailParams()
) : Params {

  init {
    require(cellSize > 0.0) { "cellSize must be positive, was $cellSize" }
    require(stationSpacing > 0.0) { "stationSpacing must be positive, was $stationSpacing" }
    require(stationsPerSegment >= 4) { "a segment needs at least four stations, was $stationsPerSegment" }
    require(fetchRays >= 4) { "fetch needs at least four rays, was $fetchRays" }
    require(fetchReach >= 1) { "fetchReach must be at least one cell, was $fetchReach" }
    require(maxBeachWidth > 0.0) { "maxBeachWidth must be positive, was $maxBeachWidth" }
  }

  override fun digest() = ParamsDigest()
    .put("cellSize", cellSize)
    .put("minPerimeter", minPerimeter)
    .put("stationSpacing", stationSpacing)
    .put("stationsPerSegment", stationsPerSegment)
    .put("maxSegmentExtent", maxSegmentExtent)
    .put("maxBeachWidth", maxBeachWidth)
    .put("fetchRays", fetchRays)
    .put("fetchReach", fetchReach)
    .put("marshSlope", marshSlope)
    .put("marshPrecipitation", marshPrecipitation)
    .put("marshExposure", marshExposure)
    .put("cliffSlope", cliffSlope)
    .put("cliffHardness", cliffHardness)
    .put("rockyExposure", rockyExposure)
    .put("rockySediment", rockySediment)
    .put("sandSediment", sandSediment)
    .put("sandExposure", sandExposure)
    .put("deltaReach", deltaReach)
    .nested("detail", detail.digest().value)
}
