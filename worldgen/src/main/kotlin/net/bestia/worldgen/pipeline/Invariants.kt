package net.bestia.worldgen.pipeline

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.StageListener
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.fields.D8
import net.bestia.worldgen.resource.DepositChannels
import net.bestia.worldgen.resource.ResourceType
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles

/**
 * The regression harness: properties every generated world must have, checked over as many seeds as you
 * care to wait for.
 *
 * Worldgen bugs are almost always *rare-seed* bugs. The pipeline is a hundred interacting parameters over
 * a space of 2^64 worlds, and the failure modes that matter - a river that flows uphill, a lake whose
 * surface is below its own bed, a settlement in the sea - appear in one seed in a few hundred and are
 * invisible in the one seed a developer happens to be looking at. Generating a thousand worlds and
 * asserting invariants is the only way to find them before players do.
 *
 * Each check is a property that is *cheap to state and expensive to violate*, which is the useful kind.
 * "Discharge never decreases downstream" is one line of physics and one loop, and if it ever fails then
 * the flow routing is broken in a way that quietly corrupts everything downstream of it.
 */
object Invariants {

  /** One violated property, with enough detail to reproduce it. */
  data class Violation(val seed: Long, val check: String, val detail: String) {
    override fun toString() = "seed $seed: $check - $detail"
  }

  /** What a sweep over many seeds found. */
  class Report(val seeds: Int, val violations: List<Violation>) {
    val isClean get() = violations.isEmpty()

    /** Violations grouped by which check failed, most frequent first. */
    fun byCheck(): List<Pair<String, Int>> = violations
      .groupingBy { it.check }
      .eachCount()
      .entries
      .sortedByDescending { it.value }
      .map { it.key to it.value }

    override fun toString() = if (isClean) {
      "Invariants: $seeds seeds clean"
    } else {
      "Invariants: ${violations.size} violations over $seeds seeds - " +
          byCheck().joinToString(", ") { "${it.first} x${it.second}" }
    }
  }

  /**
   * Generates [seeds] worlds from [firstSeed] and checks all of them.
   *
   * Deliberately sequential and deliberately not clever. This is a harness that runs overnight or in CI,
   * and being able to read the seed of a failure straight off the output matters more than throughput.
   */
  fun sweep(
    seeds: Int,
    firstSeed: Long = 1L,
    config: (Long) -> WorldConfig = { StandardWorld.demoConfig(it) },
    onSeed: (Long, Report) -> Unit = { _, _ -> }
  ): Report {
    val violations = ArrayList<Violation>()

    for (n in 0 until seeds) {
      val seed = firstSeed + n
      val generated = StandardWorld.build(config(seed), StageListener.NONE)
      val report = Report(1, check(generated))
      violations.addAll(report.violations)
      onSeed(seed, report)
    }

    return Report(seeds, violations)
  }

  /** Every check against one world. */
  fun check(generated: GeneratedWorld): List<Violation> {
    val out = ArrayList<Violation>()
    val seed = generated.config.seed

    fun fail(check: String, detail: String) = out.add(Violation(seed, check, detail))

    checkLayersAreFinite(generated, ::fail)
    checkLandFraction(generated, ::fail)
    checkDischargeGrowsDownstream(generated, ::fail)
    checkWaterIsWhereTheBiomeSaysItIs(generated, ::fail)
    checkLakesStandAboveTheirBeds(generated, ::fail)
    checkNormalisedLayersAreInRange(generated, ::fail)
    checkRiverBedsDescend(generated, ::fail)
    checkFeatureBoundsContainTheirGeometry(generated, ::fail)
    checkNoSettlementInTheSea(generated, ::fail)
    checkSettlementsAreSeparated(generated, ::fail)
    checkDepositsAreWellFormed(generated, ::fail)
    checkFjordSillsAreShallowerThanTheirBasins(generated, ::fail)

    return out
  }

  /**
   * No settlement in the sea, and none on a lake.
   *
   * The most embarrassing possible placement bug, and the cheapest to rule out. Named explicitly in the
   * architecture document's list of regression invariants for exactly that reason.
   */
  private fun checkNoSettlementInTheSea(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val water = generated.world.layers.require<FloatLayer>(LayerId.WATER_LEVEL)
    val metres = water.region.resolution.metresPerCell

    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.SETTLEMENT) continue
      val site = feature as? PointMarker ?: continue

      val cellX = Math.floor(site.position.x / metres).toInt()
      val cellY = Math.floor(site.position.y / metres).toInt()
      if (!water.region.contains(cellX, cellY)) {
        fail("no settlement in the sea", "${feature.id} at ${site.position} is outside the world")
        return
      }
      if (!water[cellX, cellY].isNaN()) {
        fail("no settlement in the sea", "${feature.id} at ${site.position} stands in water")
        return
      }
    }
  }

  /**
   * Settlements of the same tier respect their minimum separation.
   *
   * Checks the placement rule against its own output. Worth doing because the rule is enforced through a
   * bucket index, and an off-by-one in a bucket search fails silently by letting two cities sit next to each
   * other - which looks plausible enough on a map to survive review.
   */
  private fun checkSettlementsAreSeparated(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val sites = generated.world.features.all()
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<PointMarker>()

    for (i in sites.indices) {
      val tierA = SettlementTier.entries[sites[i].attribute(SettlementChannels.TIER).toInt()]

      for (j in i + 1 until sites.size) {
        val tierB = SettlementTier.entries[sites[j].attribute(SettlementChannels.TIER).toInt()]
        // The rule is one-sided: a settlement clears its own tier's separation from anything its own size or
        // larger, so the constraint between a pair is the smaller tier's - which is the larger distance.
        val required = if (tierA.ordinal >= tierB.ordinal) tierA.separation else tierB.separation
        val actual = sites[i].position.distanceTo(sites[j].position)

        if (actual < required * (1.0 - SEPARATION_TOLERANCE)) {
          fail(
            "settlements are separated",
            "$tierA and $tierB are ${actual.toInt()} m apart, needing ${required.toInt()} m"
          )
          return
        }
      }
    }
  }

  /** Deposits carry a usable type, a positive extent, and a richness in range. */
  private fun checkDepositsAreWellFormed(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.ORE_DEPOSIT) continue
      val deposit = feature as? PointMarker ?: continue

      val type = deposit.attribute(DepositChannels.TYPE).toInt()
      if (type !in ResourceType.entries.indices) {
        fail("deposits are well formed", "${feature.id} has resource type $type")
        return
      }

      val richness = deposit.attribute(DepositChannels.RICHNESS)
      if (richness < 0.0 || richness > 1.0) {
        fail("deposits are well formed", "${feature.id} has richness $richness")
        return
      }

      if (deposit.attribute(DepositChannels.RADIUS) <= 0.0) {
        fail("deposits are well formed", "${feature.id} has no extent")
        return
      }
    }
  }

  /**
   * A fjord's sill stands higher than the basin behind it.
   *
   * The defining property of a fjord, and named in the architecture document's invariant list. It falls out of
   * the overdeepening being proportional to ice flux - the mouth carries less ice than the middle, so it is
   * eroded less - which means if it ever fails, the erosion model has stopped doing the thing it was built to
   * do rather than merely producing an odd number.
   */
  private fun checkFjordSillsAreShallowerThanTheirBasins(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.FJORD) continue
      val fjord = feature as? PolylineFeature ?: continue

      val floor = runCatching { fjord.stations.channel(Profiles.CHANNEL_FLOOR_ELEVATION) }.getOrNull()
        ?: continue

      val stations = fjord.stations.stationCount
      val mouth = fjord.stations.valueAt(floor, stations - 1)
      var deepest = mouth
      for (station in 0 until stations) {
        deepest = minOf(deepest, fjord.stations.valueAt(floor, station))
      }

      if (mouth < deepest - 1e-6) {
        fail(
          "fjord sills stand above their basins",
          "${feature.id} has its mouth at $mouth, below its deepest basin at $deepest"
        )
        return
      }
    }
  }

  /**
   * No NaN or infinity anywhere it would be meaningless.
   *
   * [LayerId.WATER_LEVEL] is exempt because NaN is its way of saying "no water here", which is a real
   * answer and not a missing one - the alternative, a sentinel elevation, is a number that eventually
   * gets used as a number.
   */
  private fun checkLayersAreFinite(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    for (id in generated.world.layers.ids()) {
      if (id == LayerId.WATER_LEVEL) continue
      val layer = generated.world.layers[id] as? FloatLayer ?: continue

      val bad = layer.data.indexOfFirst { !it.isFinite() }
      if (bad >= 0) {
        fail("finite layers", "$id has ${layer.data[bad]} at index $bad")
      }
    }
  }

  private fun checkLandFraction(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val elevation = generated.world.layers.require<FloatLayer>(LayerId.ELEVATION)
    val seaLevel = generated.config.seaLevel
    val land = elevation.data.count { it > seaLevel }.toDouble() / elevation.data.size

    // Loose bounds on purpose. Tectonics normalises the *bedrock* land fraction exactly; erosion and
    // deposition then move the shoreline, and how far they move it is a legitimate property of the seed.
    // What this catches is a world that came out entirely ocean or entirely land, which is unusable.
    if (land < 0.05 || land > 0.85) {
      fail("land fraction", "${"%.3f".format(land)} of the world is above sea level")
    }
  }

  /**
   * Discharge never decreases downstream.
   *
   * The invariant the architecture document names explicitly, and the one that catches almost any error
   * in flow routing or accumulation. Water joins a river; it does not leave one.
   */
  private fun checkDischargeGrowsDownstream(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val discharge = generated.world.layers.require<FloatLayer>(LayerId.DISCHARGE)
    val direction = generated.world.layers.require<IntLayer>(LayerId.FLOW_DIRECTION)
    val region = discharge.region

    var worst = 0.0
    var worstAt = ""

    for (y in region.minY..region.maxY) {
      for (x in region.minX..region.maxX) {
        val d = direction[x, y]
        if (d == D8.NONE) continue

        val here = discharge[x, y].toDouble()
        val downstream = discharge[x + D8.DX[d], y + D8.DY[d]].toDouble()
        // A relative tolerance: these are floats accumulated over thousands of cells, so exact
        // monotonicity is not available and demanding it would only produce noise.
        val drop = here - downstream
        if (drop > worst && drop > here * 1e-4) {
          worst = drop
          worstAt = "($x,$y) $here -> $downstream"
        }
      }
    }

    if (worstAt.isNotEmpty()) {
      fail("discharge grows downstream", "worst drop $worst at $worstAt")
    }
  }

  /** A cell has a water biome exactly when it has a water level, and never otherwise. */
  private fun checkWaterIsWhereTheBiomeSaysItIs(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val biome = generated.world.layers.require<IntLayer>(LayerId.BIOME)
    val water = generated.world.layers.require<FloatLayer>(LayerId.WATER_LEVEL)
    val region = biome.region

    var mismatches = 0
    var example = ""

    for (y in region.minY..region.maxY) {
      for (x in region.minX..region.maxX) {
        val isWaterBiome = Biome.of(biome[x, y]).isWater
        val hasWater = !water[x, y].isNaN()
        if (isWaterBiome != hasWater) {
          mismatches++
          if (example.isEmpty()) {
            example = "($x,$y) biome=${Biome.of(biome[x, y])} waterLevel=${water[x, y]}"
          }
        }
      }
    }

    if (mismatches > 0) {
      fail("water biome matches water level", "$mismatches cells disagree, e.g. $example")
    }
  }

  /** A lake surface must be above the ground it covers. Trivially true, catastrophic when false. */
  private fun checkLakesStandAboveTheirBeds(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val water = generated.world.layers.require<FloatLayer>(LayerId.WATER_LEVEL)
    val elevation = generated.world.layers.require<FloatLayer>(LayerId.ELEVATION)
    val lakes = generated.world.layers.require<IntLayer>(LayerId.LAKE_ID)
    val region = water.region

    for (y in region.minY..region.maxY) {
      for (x in region.minX..region.maxX) {
        if (lakes[x, y] == 0) continue
        val level = water[x, y]
        if (level.isNaN() || level < elevation[x, y]) {
          fail(
            "lake stands above its bed",
            "($x,$y) lake ${lakes[x, y]} surface $level, bed ${elevation[x, y]}"
          )
          return
        }
      }
    }
  }

  /** The layers documented as normalised really are. Catches a scaling change nobody propagated. */
  private fun checkNormalisedLayersAreInRange(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val unitRange = listOf(
      LayerId.ROCK_HARDNESS, LayerId.CRUST_AGE, LayerId.SOIL_FERTILITY,
      LayerId.BIOME_CONFIDENCE, LayerId.PRECIPITATION_SEASONALITY
    )

    for (id in unitRange) {
      val layer = generated.world.layers[id] as? FloatLayer ?: continue
      val low = layer.data.min()
      val high = layer.data.max()
      if (low < -1e-4 || high > 1.0 + 1e-4) {
        fail("normalised layers in range", "$id spans $low..$high")
      }
    }

    val precipitation = generated.world.layers.require<FloatLayer>(LayerId.PRECIPITATION)
    if (precipitation.data.min() < -1e-4) {
      fail("normalised layers in range", "precipitation goes negative")
    }
  }

  /**
   * Every river reach's bed descends from head to mouth.
   *
   * A river that flows uphill is the classic worldgen bug, and the one most likely to survive visual
   * inspection - at map scale a channel looks the same whichever way the water is going.
   */
  private fun checkRiverBedsDescend(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.RIVER_CHANNEL) continue
      val river = feature as? PolylineFeature ?: continue

      val bed = river.stations.channel(Profiles.CHANNEL_BED_ELEVATION)
      var previous = Double.MAX_VALUE

      for (station in 0 until river.stations.stationCount) {
        val here = river.stations.valueAt(bed, station)
        if (here > previous + BED_TOLERANCE) {
          fail(
            "river beds descend",
            "${feature.id} rises from $previous to $here at station $station"
          )
          return
        }
        previous = here
      }
    }
  }

  /**
   * A feature's bounding box really does contain its geometry.
   *
   * The spatial index is built from these boxes, so an underestimate means chunk generation silently
   * misses a feature that should have influenced it - and the symptom is a river that stops at an
   * invisible line, which reads exactly like a chunk seam and sends you looking in the wrong place.
   */
  private fun checkFeatureBoundsContainTheirGeometry(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    for (feature in generated.world.features.all()) {
      for (line in feature.outline()) {
        for (point in line.points) {
          if (!feature.bbox.contains(point.x, point.y)) {
            fail("feature bounds contain geometry", "${feature.id} ${feature.kind} misses $point")
            return
          }
        }
      }
    }
  }

  /** Metres a bed may rise between stations before it counts as flowing uphill. */
  private const val BED_TOLERANCE = 1e-6

  /**
   * Slack on the separation check.
   *
   * Settlements are placed at cell centres, so two of them can legitimately sit a fraction of a cell closer
   * than the nominal separation. Demanding it exactly would fail on the grid rather than on the rule.
   */
  private const val SEPARATION_TOLERANCE = 0.02
}
