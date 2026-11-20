package net.bestia.worldgen.stronghold

import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.ParamsText

/** Tuning for [EmpyriteSeedStage]. All distances are in metres. */
data class EmpyriteSeedParams(

  /** How many seeds the pool holds at most. Only some of them are awake at a time; the zone decides how many. */
  val poolSize: Int = 32,

  /**
   * Radius of a Stronghold's grounds, where nobody may dig once an Empyrite stands.
   *
   * No ore body may reach into it, or the guild could never mine the ore that made the spot worth holding.
   */
  val groundsRadius: Double = 500.0,

  /** Ore deposits further than this from a seed do not add to its worth. */
  val resourceReach: Double = 2_000.0,

  /** Candidates are tried on a ring of this radius around every ore deposit, just outside the grounds. */
  val ringRadius: Double = 900.0,

  /** How many bearings each ring tries. */
  val ringBearings: Int = 8,

  /** From every settlement, standing or ruined. */
  val settlementClearance: Double = 5_000.0,

  /** From the next seed. Two Strongholds' grounds can then never touch. */
  val seedSeparation: Double = 5_000.0,

  /**
   * From a home settlement, whose ward stone protects a field around its arrival point.
   *
   * The ward's radius plus `SettlementSpawnPoints.MAX_ARRIVAL_OFFSET_METRES`, rounded up: the stage cannot
   * see the arrival point, so it keeps clear of every point one could be.
   */
  val homeClearance: Double = 6_100.0,

  /** From history sites, cave mouths, roads and POIs: what `PoiParams` keeps clear of, for the same reasons. */
  val siteClearance: Double = 400.0,
  val caveClearance: Double = 120.0,
  val roadClearance: Double = 40.0,
  val poiClearance: Double = 300.0,

  /** Metres above sea level a seed must stand. */
  val freeboard: Double = 12.0
) : Params {

  init {
    require(poolSize > 0) { "poolSize must be positive, was $poolSize" }
    require(groundsRadius > 0.0) { "groundsRadius must be positive, was $groundsRadius" }
    require(ringRadius > groundsRadius) { "ringRadius must lie outside the grounds, was $ringRadius" }
    require(resourceReach > ringRadius) { "resourceReach must reach past the ring, was $resourceReach" }
    require(ringBearings > 0) { "ringBearings must be positive, was $ringBearings" }
    require(seedSeparation >= 2 * groundsRadius) { "two seeds' grounds must not touch" }
    require(settlementClearance >= 0.0 && homeClearance >= 0.0) { "clearances must not be negative" }
    require(siteClearance >= 0.0 && caveClearance >= 0.0) { "clearances must not be negative" }
    require(roadClearance >= 0.0 && poiClearance >= 0.0) { "clearances must not be negative" }
    require(freeboard >= 0.0) { "freeboard must not be negative, was $freeboard" }
  }

  fun overriddenBy(source: ParamsText.ParamsSource) = copy(
    poolSize = source.int("poolSize", poolSize),
    groundsRadius = source.double("groundsRadius", groundsRadius),
    resourceReach = source.double("resourceReach", resourceReach),
    ringRadius = source.double("ringRadius", ringRadius),
    ringBearings = source.int("ringBearings", ringBearings),
    settlementClearance = source.double("settlementClearance", settlementClearance),
    seedSeparation = source.double("seedSeparation", seedSeparation),
    homeClearance = source.double("homeClearance", homeClearance),
    siteClearance = source.double("siteClearance", siteClearance),
    caveClearance = source.double("caveClearance", caveClearance),
    roadClearance = source.double("roadClearance", roadClearance),
    poiClearance = source.double("poiClearance", poiClearance),
    freeboard = source.double("freeboard", freeboard)
  )

  override fun digest() = ParamsDigest()
    .put("poolSize", poolSize)
    .put("groundsRadius", groundsRadius)
    .put("resourceReach", resourceReach)
    .put("ringRadius", ringRadius)
    .put("ringBearings", ringBearings)
    .put("settlementClearance", settlementClearance)
    .put("seedSeparation", seedSeparation)
    .put("homeClearance", homeClearance)
    .put("siteClearance", siteClearance)
    .put("caveClearance", caveClearance)
    .put("roadClearance", roadClearance)
    .put("poiClearance", poiClearance)
    .put("freeboard", freeboard)
}
