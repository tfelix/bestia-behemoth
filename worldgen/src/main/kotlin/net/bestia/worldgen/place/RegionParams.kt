package net.bestia.worldgen.place

/**
 * How the region partition is shaped.
 *
 * Not registered in `pipeline/WorldParams` and deliberately so: `PlaceRegions` is not a `Stage`, so
 * none of this reaches `pipelineVersion` and changing a value here cannot invalidate a stored world.
 * It also means these are not reachable from a params file - if that is ever wanted, the partition has
 * to become a stage first, and the reason not to is in `PlaceRegions`' own KDoc.
 *
 * **Set every weight to zero and the partition is exact Voronoi over the seed points.** That is the
 * A/B for judging whether the cost field is earning its keep, and it is a params change rather than a
 * revert precisely so it stays cheap to run.
 */
data class RegionParams(

  /**
   * Metres between region seed points, before cost weighting pulls the boundaries around.
   *
   * **Absolute, and not put through `WorldConfig.scaleByLength`.** `climate/WeatherRegions` makes this
   * argument for its own 16 km and it transfers unchanged: how far a player walks before they are
   * somewhere else is a fact about the player, not about how big the world is. A 512 km world does not
   * want names four times coarser than a 128 km one.
   */
  val spacing: Double = 6_000.0,

  /**
   * How much coarser water regions are than land ones.
   *
   * Open sea has nothing in it to distinguish one stretch from the next, so naming it at the same grain
   * as land spends names on ground nobody can tell apart - on the genesis world half the regions came
   * out as ocean, which is half the name pool gone on featureless water. Sailing further between labels
   * is also simply truer: a sea is one place for longer than a valley is.
   */
  val waterSpacingFactor: Double = 2.5,

  /**
   * Extra cost for a step that crosses into a different biome.
   *
   * The single most useful weight of the four: biome edges are where the land visibly changes, and a
   * name that stops where the forest stops needs no further justification.
   */
  val biomePenalty: Double = 1.5,

  /** Cost per metre of elevation change across a step. Makes ridges into boundaries. */
  val reliefPenalty: Double = 0.02,

  /** Extra cost for stepping onto a cell carrying at least [riverDischarge]. */
  val riverPenalty: Double = 6.0,

  /**
   * Discharge in cubic metres per second at which a channel is big enough to divide two places.
   *
   * `AlluviumStage.minDischarge` is 0.25 and marks "carries enough water to build a fan", which is a
   * far smaller stream than anything a person would name two sides of. Best set by looking at
   * `viewer/RegionOverlay` rather than reasoned about - that is what it is for.
   */
  val riverDischarge: Double = 1.0,

  /**
   * Regions smaller than this many cells are absorbed into a neighbour.
   *
   * A sliver gets a name that no player will ever see attached to enough ground to mean anything, and
   * it crowds the name pool for the region it was carved off. An island with no neighbour of its own
   * water class is kept regardless - see `PlaceRegions.mergeSlivers`.
   */
  val minCells: Int = 6
) {

  init {
    require(spacing > 0.0) { "spacing must be positive, was $spacing" }
    require(waterSpacingFactor > 0.0) {
      "waterSpacingFactor must be positive, was $waterSpacingFactor"
    }
    require(biomePenalty >= 0.0) { "biomePenalty must not be negative, was $biomePenalty" }
    require(reliefPenalty >= 0.0) { "reliefPenalty must not be negative, was $reliefPenalty" }
    require(riverPenalty >= 0.0) { "riverPenalty must not be negative, was $riverPenalty" }
    require(riverDischarge >= 0.0) { "riverDischarge must not be negative, was $riverDischarge" }
    require(minCells >= 0) { "minCells must not be negative, was $minCells" }
  }
}
