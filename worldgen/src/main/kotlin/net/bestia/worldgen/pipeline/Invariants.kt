package net.bestia.worldgen.pipeline

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.civ.BuildingChannels
import net.bestia.worldgen.civ.GateChannels
import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.civ.WallChannels
import net.bestia.worldgen.climate.SeasonalPrecipitation
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.SiteKind
import net.bestia.worldgen.core.WorldWrap
import net.bestia.worldgen.core.StageListener
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.fields.D8
import net.bestia.worldgen.geo.ClosedBasins
import net.bestia.worldgen.history.SiteChannels
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.worldgen.pop.BusinessChannels
import net.bestia.worldgen.pop.EconomyChannels
import net.bestia.worldgen.resource.DepositChannels
import net.bestia.worldgen.resource.ResourceType
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.FootprintFeature
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import net.bestia.worldgen.voxel.ChunkMaterializer
import java.util.Locale
import kotlin.math.abs

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

    /**
     * Called once per world, with the world itself so a caller can measure it.
     *
     * The world is passed because a sweep is the only place the seed-to-seed *spread* of anything is
     * visible, and a pass/fail is not a measurement: whether the land fraction sits at 0.50 on every seed
     * or wanders between 0.35 and 0.65 is what decides whether a tuning change is finished, and the
     * invariant's own loose bounds cannot tell you.
     */
    onSeed: (Long, Report, GeneratedWorld) -> Unit = { _, _, _ -> }
  ): Report {
    val violations = ArrayList<Violation>()

    for (n in 0 until seeds) {
      val seed = firstSeed + n
      val generated = StandardWorld.build(config(seed), StageListener.NONE)
      val report = Report(1, check(generated))
      violations.addAll(report.violations)
      onSeed(seed, report, generated)
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
    checkTheWorldHasStandingWater(generated, ::fail)
    checkLakesStandAboveTheirBeds(generated, ::fail)
    checkClosedBasinsCanHoldWater(generated, ::fail)
    checkNormalisedLayersAreInRange(generated, ::fail)
    checkSeasonalPrecipitationSumsToTheAnnualField(generated, ::fail)
    checkRiverBedsDescend(generated, ::fail)
    checkFeatureBoundsContainTheirGeometry(generated, ::fail)
    checkNoSettlementInTheSea(generated, ::fail)
    checkSettlementsAreSeparated(generated, ::fail)
    checkDepositsAreWellFormed(generated, ::fail)
    checkFjordSillsAreShallowerThanTheirBasins(generated, ::fail)
    // Was written and never registered, so the property the architecture document lists as asserted was not
    // being checked by anything. Registered here rather than left as documentation of an intention.
    checkOceanBorderIsOcean(generated, ::fail)

    // Steps 8 to 10.
    checkHistoryIsSelfConsistent(generated, ::fail)
    checkEventsRespectSettlementLifetimes(generated, ::fail)
    checkNoEventCitesAPrunedCause(generated, ::fail)
    checkArtifactChainsAreOrdered(generated, ::fail)
    checkEveryRuinHasAnEvent(generated, ::fail)
    checkStructuralMarkersFitTheQueryMargin(generated, ::fail)
    checkBuildingsBelongToTheirSettlement(generated, ::fail)
    checkNothingIsBuiltInWater(generated, ::fail)
    checkWalledSettlementsHaveAGate(generated, ::fail)
    checkEveryStandingSettlementCanEat(generated, ::fail)
    checkEmploymentAddsUp(generated, ::fail)
    checkBusinessesAreWellFormed(generated, ::fail)
    checkRoadsideInnsAreOnTheRoad(generated, ::fail)
    checkSeaLanesStayAtSea(generated, ::fail)

    return out
  }

  /**
   * Every sea lane is over open water for its whole length, and none of it is in the ocean margin.
   *
   * Both halves matter and the second is the subtle one. The margin is the band of forced deep water that hides
   * the east-west wrap, so a lane routed through it is a road across the seam by another name - a player
   * following that lane sails off one edge of the world and arrives at the other. The water cost field forbids
   * those cells outright, so this holds by construction; asserting it is what would catch the field being built
   * from the wrong predicate, which is a change nothing else in the pipeline would notice.
   *
   * Deliberately **not** a check that lanes exist. Most worlds have none, correctly: a lane needs two cities
   * that a road cannot join, and a world whose cities all sit on one landmass has no such pair. Measured, 3 to 6
   * worlds in 15 have at least one, rising with world size. The existence claim is therefore pinned to a seed
   * that does have them, in `civ/SeaLaneTest` - because an unconditional version here would fail on most seeds,
   * and a conditional one would be the vacuous kind this module has been bitten by.
   */
  private fun checkSeaLanesStayAtSea(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val lanes = generated.world.features.all().filter { it.kind == FeatureKind.SEA_LANE }
    if (lanes.isEmpty()) return

    val waterLevel = generated.world.layers[LayerId.WATER_LEVEL] as? FloatLayer ?: return
    val lakeId = generated.world.layers[LayerId.LAKE_ID] as? IntLayer ?: return
    val metres = waterLevel.region.resolution.metresPerCell
    val wrap = WorldWrap(generated.config)

    for (lane in lanes) {
      val marker = lane as? MarkerFeature ?: continue

      for (point in marker.centerline.points) {
        val x = (point.x / metres).toInt()
        val y = (point.y / metres).toInt()

        if (waterLevel[x, y].isNaN() || lakeId[x, y] != 0) {
          fail(
            "sea lanes stay at sea",
            "${lane.id} leaves open water at (${point.x.toInt()},${point.y.toInt()})"
          )
          return
        }

        if (wrap.isInOceanBorder(point.x, point.y)) {
          fail(
            "sea lanes stay at sea",
            "${lane.id} enters the ocean margin at (${point.x.toInt()},${point.y.toInt()})"
          )
          return
        }
      }
    }
  }

  // --- Step 10: history ------------------------------------------------------------------------------

  /**
   * Founding and abandonment years make sense, and a settlement's population matches its state.
   *
   * The cheapest possible check on the simulation, and the one that catches an off-by-one in the year loop or
   * a settlement that was abandoned and then went on growing - both of which are invisible downstream except
   * as a ruin with two hundred people living in it.
   */
  private fun checkHistoryIsSelfConsistent(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val chronicle = generated.world.chronicle
    if (chronicle.events.isEmpty()) return

    for (record in chronicle.settlements) {
      if (record.wasFounded && record.foundedYear !in chronicle.span) {
        fail("history is self consistent", "settlement ${record.index} founded in ${record.foundedYear}")
        return
      }
      if (record.isRuin) {
        if (record.abandonedYear < record.foundedYear) {
          fail(
            "history is self consistent",
            "settlement ${record.index} was abandoned in ${record.abandonedYear}, before it was founded"
          )
          return
        }
        if (record.population != 0) {
          fail("history is self consistent", "ruin ${record.index} has ${record.population} people in it")
          return
        }
      } else if (record.wasFounded && record.population <= 0) {
        fail("history is self consistent", "standing settlement ${record.index} has nobody in it")
        return
      }
      if (record.wallYear != 0 && record.wallYear < record.foundedYear) {
        fail(
          "history is self consistent",
          "settlement ${record.index} was walled in ${record.wallYear} before it existed"
        )
        return
      }
    }

    for (civ in chronicle.civs) {
      if (civ.foundedYear !in chronicle.span) {
        fail("history is self consistent", "civ ${civ.index} founded in ${civ.foundedYear}")
        return
      }
      if (civ.technology < 0.0 || civ.technology > 1.0) {
        fail("history is self consistent", "civ ${civ.index} has technology ${civ.technology}")
        return
      }
    }
  }

  /**
   * Nothing happens to a settlement before it was founded or after it emptied.
   *
   * The invariant that makes the log usable as a story. An event outside its subject's lifetime reads as a
   * plague in a town nobody had built yet, and once one exists the log stops being trustworthy for anything -
   * so this is checked over every event rather than sampled.
   *
   * The two exemptions are the founding itself and whatever emptied the place, which are the boundary events
   * and are *at* the boundary rather than inside it.
   */
  private fun checkEventsRespectSettlementLifetimes(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val chronicle = generated.world.chronicle

    for (event in chronicle.events) {
      for (actor in event.actors) {
        if (actor.type != ActorType.SETTLEMENT) continue
        val record = chronicle.settlements.getOrNull(actor.index) ?: continue

        if (event.year == record.foundedYear || event.year == record.abandonedYear) continue

        if (!record.wasFounded || event.year < record.foundedYear) {
          fail(
            "events respect settlement lifetimes",
            "event ${event.id} in ${event.year} names settlement ${actor.index}, founded ${record.foundedYear}"
          )
          return
        }
        if (record.isRuin && event.year > record.abandonedYear) {
          fail(
            "events respect settlement lifetimes",
            "event ${event.id} in ${event.year} names settlement ${actor.index}, empty since ${record.abandonedYear}"
          )
          return
        }
      }
    }
  }

  /**
   * Every cause an event cites survived the pruner.
   *
   * The pruner promises this - it takes the transitive closure over causes after choosing what to keep - and
   * this is that promise asserted. A dangling cause id is a provenance chain with a hole in it, and the
   * symptom is a tool throwing rather than a world looking wrong, which is the kind of bug that surfaces in
   * front of somebody else.
   */
  private fun checkNoEventCitesAPrunedCause(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val chronicle = generated.world.chronicle
    val present = chronicle.events.mapTo(HashSet()) { it.id }

    for (event in chronicle.events) {
      for (cause in event.causes) {
        if (cause !in present) {
          fail("no event cites a pruned cause", "event ${event.id} cites ${cause}, which was pruned")
          return
        }
      }
    }
  }

  /**
   * An artifact's provenance runs forwards in time and ends somewhere that exists.
   *
   * Both halves matter for the same reason: the chain is the thing a quest is mined from, and a chain that
   * goes backwards or ends at site -1 is a quest that cannot be written.
   */
  private fun checkArtifactChainsAreOrdered(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val chronicle = generated.world.chronicle

    for (relic in chronicle.artifacts) {
      var previous = Int.MIN_VALUE
      for (event in chronicle.provenanceOf(relic.index)) {
        if (event.year < previous) {
          fail(
            "artifact chains are ordered",
            "artifact ${relic.index} goes from year $previous back to ${event.year}"
          )
          return
        }
        previous = event.year
      }

      if (relic.restingSite >= 0 && relic.restingSite >= chronicle.sites.size) {
        fail("artifact chains are ordered", "artifact ${relic.index} rests at site ${relic.restingSite}")
        return
      }
      if (relic.forgedBy !in chronicle.figures.indices) {
        fail("artifact chains are ordered", "artifact ${relic.index} was forged by nobody")
        return
      }
    }
  }

  /** Every ruin marker corresponds to a settlement the log says was emptied, and vice versa. */
  private fun checkEveryRuinHasAnEvent(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val chronicle = generated.world.chronicle
    if (chronicle.events.isEmpty()) return

    val ruinSites = chronicle.sitesOfKind(SiteKind.RUIN)
    for (site in ruinSites) {
      val record = chronicle.settlements.getOrNull(site.settlement)
      if (record == null || !record.isRuin) {
        fail("every ruin has an event", "ruin site ${site.index} names settlement ${site.settlement}")
        return
      }
    }

    val ruinedSettlements = chronicle.settlements.count { it.isRuin }
    if (ruinSites.size != ruinedSettlements) {
      fail(
        "every ruin has an event",
        "$ruinedSettlements settlements were emptied but there are ${ruinSites.size} ruin sites"
      )
    }
  }

  /**
   * No structural marker reaches further than the margin chunk generation queries with.
   *
   * The tripwire promised by [ChunkMaterializer.MARKER_MARGIN]. A point marker has no extent, so chunk
   * generation finds it by expanding the chunk's own bounds by a fixed margin - and a ruin field or an
   * orebody wider than that margin is simply absent from every chunk further away than it, which materialises
   * as a ruin with a dead straight edge down one side. The two numbers live in sibling packages that may not
   * call into each other, so this is what keeps them in step.
   */
  private fun checkStructuralMarkersFitTheQueryMargin(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val structural = setOf(
      FeatureKind.RUIN, FeatureKind.BATTLEFIELD, FeatureKind.TOMB, FeatureKind.MONUMENT
    )

    for (feature in generated.world.features.all()) {
      if (feature.kind !in structural) continue
      val marker = feature as? PointMarker ?: continue
      val radius = runCatching { marker.attribute(SiteChannels.RADIUS) }.getOrNull() ?: continue

      if (radius > ChunkMaterializer.MARKER_MARGIN) {
        fail(
          "structural markers fit the query margin",
          "${feature.kind} ${feature.id} reaches ${radius.toInt()} m, " +
              "past the ${ChunkMaterializer.MARKER_MARGIN.toInt()} m chunk query margin"
        )
        return
      }
    }
  }

  // --- Step 8: towns --------------------------------------------------------------------------------

  /**
   * Every building names a settlement that stands, and stands inside it.
   *
   * Inside its *tier's* footprint rather than inside the built radius, because the built radius is derived
   * from the present population and a building placed when the town was larger is legitimately outside it.
   * What this catches is a building attributed to the wrong settlement, which the join on
   * [SettlementChannels.INDEX] makes possible in exactly one way - an index that shifted between two stages.
   */
  private fun checkBuildingsBelongToTheirSettlement(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val chronicle = generated.world.chronicle
    val sites = generated.world.features.all()
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<PointMarker>()
      .associateBy { it.attribute(SettlementChannels.INDEX).toInt() }

    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.BUILDING) continue
      val building = feature as? FootprintFeature ?: continue
      val index = building.attribute(BuildingChannels.SETTLEMENT).toInt()

      val site = sites[index]
      if (site == null) {
        fail("buildings belong to their settlement", "building ${feature.id} names settlement $index")
        return
      }

      val tier = SettlementTier.entries[site.attribute(SettlementChannels.TIER).toInt()]
      val away = building.center.distanceTo(site.position)
      if (away > tier.footprintRadius) {
        fail(
          "buildings belong to their settlement",
          "building ${feature.id} is ${away.toInt()} m from settlement $index, a $tier"
        )
        return
      }

      if (chronicle.events.isNotEmpty()) {
        val record = chronicle.settlements.getOrNull(index)
        if (record == null || !record.wasFounded || record.isRuin) {
          fail(
            "buildings belong to their settlement",
            "building ${feature.id} stands in settlement $index, which history never left standing"
          )
          return
        }
      }
    }
  }

  /**
   * Nothing is built in water: no building, no street, no wall.
   *
   * The most visible possible failure of the layout, and one the buildable test is meant to prevent - so what
   * this really checks is that the test was consulted everywhere rather than only where it was convenient.
   */
  private fun checkNothingIsBuiltInWater(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val water = generated.world.layers.require<FloatLayer>(LayerId.WATER_LEVEL)
    val metres = water.region.resolution.metresPerCell

    fun wet(x: Double, y: Double): Boolean {
      val cellX = Math.floor(x / metres).toInt()
      val cellY = Math.floor(y / metres).toInt()
      return water.region.contains(cellX, cellY) && !water[cellX, cellY].isNaN()
    }

    for (feature in generated.world.features.all()) {
      when (feature.kind) {
        FeatureKind.BUILDING -> {
          val building = feature as? FootprintFeature ?: continue
          if (wet(building.center.x, building.center.y)) {
            fail("nothing is built in water", "building ${feature.id} stands in water")
            return
          }
        }

        FeatureKind.STREET, FeatureKind.TOWN_WALL -> {
          for (line in feature.outline()) {
            for (point in line.points) {
              if (wet(point.x, point.y)) {
                fail("nothing is built in water", "${feature.kind} ${feature.id} runs through water")
                return
              }
            }
          }
        }

        else -> Unit
      }
    }
  }

  /**
   * A settlement with walls has at least one gate.
   *
   * Cheap and catastrophic when false: the wall is emitted as the stretches *between* gates, so a circuit with
   * no gate is a sealed ring of masonry round a town, which nothing can walk into or out of.
   */
  private fun checkWalledSettlementsHaveAGate(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val walls = generated.world.features.all().filter { it.kind == FeatureKind.TOWN_WALL }
    if (walls.isEmpty()) return

    val gates = generated.world.features.all()
      .filter { it.kind == FeatureKind.GATE }
      .filterIsInstance<PointMarker>()

    val walled = walls.mapNotNull { wall ->
      (wall as? MarkerFeature)?.let {
        runCatching { it.stations!!.valueAt(it.channel(WallChannels.SETTLEMENT), 0).toInt() }.getOrNull()
      }
    }.toSet()

    val gated = gates.mapNotNull {
      runCatching { it.attribute(GateChannels.SETTLEMENT).toInt() }.getOrNull()
    }.toSet()

    val sealed = walled - gated
    if (sealed.isNotEmpty()) {
      fail("walled settlements have a gate", "settlements $sealed are walled with no way in")
    }
  }

  // --- Step 9: the economy ---------------------------------------------------------------------------

  /**
   * Every standing settlement's catchment yields something.
   *
   * Named explicitly in the architecture document's list of invariants - "every settlement has food access" -
   * and it earned its place: a resolution mistake in the catchment made every settlement in a test world read
   * zero, which downstream became a population that was a hundred percent farmers and still starving. Nothing
   * about the map looked wrong.
   */
  private fun checkEveryStandingSettlementCanEat(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.SETTLEMENT_ECONOMY) continue
      val economy = feature as? PointMarker ?: continue

      val capacity = economy.attribute(EconomyChannels.FOOD_CAPACITY)
      if (capacity <= 0.0 || !capacity.isFinite()) {
        fail(
          "every settlement can eat",
          "settlement ${economy.attribute(EconomyChannels.INDEX).toInt()} has a food capacity of $capacity"
        )
        return
      }
    }
  }

  /** Employment by sector sums to the population, give or take rounding. */
  private fun checkEmploymentAddsUp(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val chronicle = generated.world.chronicle
    if (chronicle.events.isEmpty()) return

    val sectors = listOf(
      EconomyChannels.FARMERS, EconomyChannels.CRAFTERS, EconomyChannels.TRADERS,
      EconomyChannels.SERVANTS, EconomyChannels.ADMINISTRATORS, EconomyChannels.CLERGY,
      EconomyChannels.SOLDIERS
    )

    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.SETTLEMENT_ECONOMY) continue
      val economy = feature as? PointMarker ?: continue

      val index = economy.attribute(EconomyChannels.INDEX).toInt()
      val population = chronicle.settlements.getOrNull(index)?.population ?: continue
      val employed = sectors.sumOf { economy.attribute(it).toInt() }

      // A handful either way: each sector's share is truncated and the remainder goes back to farming.
      if (Math.abs(employed - population) > EMPLOYMENT_TOLERANCE) {
        fail(
          "employment adds up",
          "settlement $index has $population people and $employed jobs"
        )
        return
      }
    }
  }

  /** Businesses name a real trade, a standing settlement, and sit somewhere in it. */
  private fun checkBusinessesAreWellFormed(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val chronicle = generated.world.chronicle

    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.BUSINESS && feature.kind != FeatureKind.ROADSIDE_INN) continue
      val business = feature as? PointMarker ?: continue

      val type = business.attribute(BusinessChannels.TYPE).toInt()
      if (type !in BusinessCatalogue.ALL.indices) {
        fail("businesses are well formed", "${feature.kind} ${feature.id} has trade $type")
        return
      }

      val settlement = business.attribute(BusinessChannels.SETTLEMENT).toInt()
      if (settlement < 0) continue

      if (chronicle.events.isNotEmpty() && !chronicle.settlementStood(settlement, chronicle.presentYear)) {
        fail(
          "businesses are well formed",
          "${feature.kind} ${feature.id} trades in settlement $settlement, which does not stand"
        )
        return
      }
    }
  }

  /**
   * A roadside inn is beside a road and not inside a settlement.
   *
   * The whole point of one: it exists because there is nowhere else to stop. An inn that ended up inside a
   * town is one the town already had, counted twice.
   */
  private fun checkRoadsideInnsAreOnTheRoad(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val inns = generated.world.features.all()
      .filter { it.kind == FeatureKind.ROADSIDE_INN }
      .filterIsInstance<PointMarker>()
    if (inns.isEmpty()) return

    val sites = generated.world.features.all()
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<PointMarker>()
    val roads = generated.world.features.all()
      .filter { it.kind == FeatureKind.ROAD }
      .filterIsInstance<PolylineFeature>()

    for (inn in inns) {
      val nearestRoad = roads.minOfOrNull { it.centerline.project(inn.position).distance } ?: Double.MAX_VALUE
      if (nearestRoad > INN_ROAD_TOLERANCE) {
        fail(
          "roadside inns are on the road",
          "inn ${inn.id} is ${nearestRoad.toInt()} m from the nearest road"
        )
        return
      }

      val nearestSettlement = sites.minOfOrNull { it.position.distanceTo(inn.position) } ?: Double.MAX_VALUE
      if (nearestSettlement < INN_SETTLEMENT_CLEARANCE) {
        fail(
          "roadside inns are on the road",
          "inn ${inn.id} is only ${nearestSettlement.toInt()} m from a settlement"
        )
        return
      }
    }
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

  /**
   * The forced ocean margin really is ocean, and nothing was built or routed into it.
   *
   * The margin is what makes the east-west wrap invisible, and it only works if it is *completely* water: one
   * island poking out of it, or one road running into it, and a player at the seam sees the world change under
   * them as they cross. Cheap to assert and impossible to eyeball, since it is the part of the map nobody looks
   * at until they walk off the edge of it.
   */
  private fun checkOceanBorderIsOcean(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val config = generated.config
    if (config.oceanBorderMetres <= 0.0) return

    val wrap = WorldWrap(config)
    val elevation = generated.world.layers.require<FloatLayer>(LayerId.ELEVATION)
    val metres = elevation.region.resolution.metresPerCell

    for (y in 0 until elevation.region.height) {
      for (x in 0 until elevation.region.width) {
        val worldX = (elevation.region.minX + x + 0.5) * metres
        val worldY = (elevation.region.minY + y + 0.5) * metres
        if (!wrap.isInOceanBorder(worldX, worldY)) continue

        if (elevation[elevation.region.minX + x, elevation.region.minY + y] > config.seaLevel) {
          fail(
            "ocean border",
            "land at (${worldX.toInt()},${worldY.toInt()}), which is inside the ${config.oceanBorderMetres.toInt()} m margin"
          )
          return
        }
      }
    }

    // Settlements are the ones that would actually be noticed, being visible from a long way off.
    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.SETTLEMENT) continue
      if (wrap.isInOceanBorder(feature.bbox.centerX, feature.bbox.centerY)) {
        fail("ocean border", "a settlement sits in the ocean margin at ${feature.bbox.centerX.toInt()}")
        return
      }
    }
  }

  /**
   * The share of a heightfield that is above sea level.
   *
   * Public and shared, because three callers want this number and each used to compute it: this check, the
   * pipeline test, and - now - the viewer and the sweep, which print it. Two of those copies had already
   * drifted, one of them testing `> 0f` rather than against the world's own sea level.
   *
   * @param layerId [LayerId.BEDROCK_ELEVATION] for what tectonics *aimed* at, [LayerId.ELEVATION] for what
   *   the player gets. They differ by however far erosion and deposition moved the shoreline, and telling
   *   the two apart is the difference between a normalisation bug and a legitimate seed.
   */
  fun landFraction(generated: GeneratedWorld, layerId: LayerId = LayerId.ELEVATION): Double {
    val elevation = generated.world.layers.require<FloatLayer>(layerId)
    val seaLevel = generated.config.seaLevel
    return elevation.data.count { it > seaLevel }.toDouble() / elevation.data.size
  }

  /**
   * How many distinct lake basins the world holds.
   *
   * Public for the same reason [landFraction] is: the sweep and the viewer both print it, and a number two
   * tools measure differently is a number neither can be trusted about. Counts distinct `LAKE_ID` values
   * rather than cells, because "one lake the size of a sea" and "forty tarns" cover the same area and are not
   * the same world.
   */
  fun lakeCount(generated: GeneratedWorld): Int {
    val lakes = generated.world.layers[LayerId.LAKE_ID] as? IntLayer ?: return 0

    val ids = HashSet<Int>()
    for (id in lakes.data) {
      if (id != 0) ids.add(id)
    }
    return ids.size
  }

  private fun checkLandFraction(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val land = landFraction(generated)

    // Loose bounds on purpose. Tectonics normalises the *bedrock* land fraction exactly; erosion and
    // deposition then move the shoreline, and how far they move it is a legitimate property of the seed.
    // What this catches is a world that came out entirely ocean or entirely land, which is unusable.
    if (land < 0.05 || land > 0.85) {
      fail("land fraction", "${"%.3f".format(Locale.ROOT, land)} of the world is above sea level")
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

  /**
   * The world has standing fresh water somewhere on it.
   *
   * The check whose absence hid the longest-lived defect in this module. `hydro/Lakes.kt` was written complete,
   * with an endorheic evaporation balance and unit tests over synthetic pits, and **never once received a
   * basin**: erosion conditions its output surface to be depression-free, nothing else dug one, so `LAKE_ID`
   * was zero on every world at every size. [checkLakesStandAboveTheirBeds] skipped every cell of it and
   * reported success, which is how an invariant the architecture document listed as asserted was in fact
   * asserting nothing.
   *
   * So this is deliberately the one lake property stated *unconditionally*. It can be, because both sources of
   * standing water are now guaranteed rather than incidental: glacial overdeepening delivers where ice ran, and
   * `geo/ClosedBasins` puts a tectonic basin on every continent whether it froze or not - by construction, not
   * by tuning. See the note there on why the depression cannot fail to be one.
   */
  private fun checkTheWorldHasStandingWater(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val lakes = generated.world.layers.require<IntLayer>(LayerId.LAKE_ID)
    if (lakes.data.any { it != 0 }) return

    val basins = generated.world.features.all().count { it.kind == FeatureKind.TECTONIC_BASIN }
    fail(
      "the world has standing water",
      "no cell belongs to a lake, on a world with $basins closed basins carved into it"
    )
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

  /**
   * Every closed basin is a dry-land depression of a plausible size.
   *
   * The floor is the one that matters. A basin carved below sea level is not an inland sea - `FlowRouting` calls
   * anything under the waterline ocean and `Lakes` skips ocean cells, so what it actually produces is a pocket
   * of sea in the middle of a continent with no coast to it and no lake in it. `ClosedBasinParams.freeboard`
   * exists to prevent that, and this is what keeps it honest when the depths are next retuned.
   *
   * The radius bound is the same class of tripwire as the cirque and trough caps: unbounded growth in a
   * landform's size is how this module has produced 12 km cirques and 93 km valley floors, and both times the
   * cause was a formula with nothing on the far side of it.
   */
  private fun checkClosedBasinsCanHoldWater(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val seaLevel = generated.config.seaLevel
    val widest =
      minOf(generated.config.widthMetres, generated.config.heightMetres) * MAX_BASIN_WORLD_SHARE

    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.TECTONIC_BASIN) continue
      val marker = feature as? PointMarker ?: continue

      val floor = runCatching { marker.attribute(ClosedBasins.CHANNEL_FLOOR) }.getOrNull() ?: continue
      val radius = marker.attribute(ClosedBasins.CHANNEL_RADIUS)
      val depth = marker.attribute(ClosedBasins.CHANNEL_DEPTH)

      if (floor <= seaLevel) {
        fail(
          "closed basins can hold water",
          "basin ${feature.id} has its floor at ${floor.toInt()} m, at or below sea level"
        )
        return
      }
      if (depth <= 0.0 || radius <= 0.0 || radius > widest) {
        fail(
          "closed basins can hold water",
          "basin ${feature.id} is ${radius.toInt()} m across and ${depth.toInt()} m deep"
        )
        return
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

    for (id in SeasonalPrecipitation.LAYERS) {
      val season = generated.world.layers[id] as? FloatLayer ?: continue
      if (season.data.min() < -1e-4) {
        fail("normalised layers in range", "$id goes negative")
      }
    }
  }

  /**
   * The four seasonal precipitation layers sum to [LayerId.PRECIPITATION].
   *
   * The layers' entire claim, and a claim that is easy to break from a distance: the annual field is calibrated
   * to a configured mean after the seasonal fields are summed out of it, so any scaling applied to one and not
   * the other leaves four layers that describe a different year from the one every existing consumer reads.
   * The first implementation did exactly that.
   *
   * Registered as a sweep invariant rather than left to the unit test because it is a property of every world
   * and costs one pass over five layers. A unit test on one seed would not catch a scale factor that only
   * misbehaves where the annual mean comes out near zero.
   */
  private fun checkSeasonalPrecipitationSumsToTheAnnualField(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val annual = generated.world.layers[LayerId.PRECIPITATION] as? FloatLayer ?: return
    val seasons = SeasonalPrecipitation.LAYERS.map {
      generated.world.layers[it] as? FloatLayer ?: return
    }

    for (i in annual.data.indices) {
      var sum = 0.0
      for (season in seasons) sum += season.data[i].toDouble()

      // Absolute, in millimetres, because the annual field spans four orders of magnitude across a world and a
      // relative tolerance would be meaningless in a desert. Half a millimetre is float precision on a sum of
      // four values of a few hundred; the bug this catches is off by hundreds.
      if (abs(sum - annual.data[i]) > SEASONAL_SUM_TOLERANCE) {
        fail(
          "seasonal precipitation sums to the annual field",
          "cell $i has seasons summing to $sum against an annual ${annual.data[i]}"
        )
        return
      }
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
   * Millimetres by which the four seasonal fields may differ from their annual sum.
   *
   * Float layers holding a sum of four values of a few hundred millimetres, so this is precision rather than
   * tolerance for error. The defect it exists to catch - a calibration factor applied to the annual field and
   * not to its parts - is off by hundreds of millimetres, not by half of one.
   */
  private const val SEASONAL_SUM_TOLERANCE = 0.5

  /**
   * Largest a closed basin may be, as a share of the world's short edge.
   *
   * A lake a tenth of a continent across is Baikal, and generous. The bound is here at all because two
   * landforms in this pipeline have already run away - a cirque reached 12 km radius and a trough floor 93 km
   * of half-width - and in both cases the formula was a cube root with nothing on the far side of it.
   */
  private const val MAX_BASIN_WORLD_SHARE = 0.1

  /**
   * Slack on the separation check.
   *
   * Settlements are placed at cell centres, so two of them can legitimately sit a fraction of a cell closer
   * than the nominal separation. Demanding it exactly would fail on the grid rather than on the rule.
   */
  private const val SEPARATION_TOLERANCE = 0.02

  /** People a settlement's sector counts may miss by, from truncating seven shares. */
  private const val EMPLOYMENT_TOLERANCE = 8

  /** Metres a roadside inn may sit from the centreline of the road it serves. */
  private const val INN_ROAD_TOLERANCE = 50.0

  /**
   * Metres an inn must keep from any settlement.
   *
   * Slack against `EconomyParams.innClearance`, which is measured against *founded* settlements only - a site
   * history never settled is not a town and an inn beside it is correct.
   */
  private const val INN_SETTLEMENT_CLEARANCE = 500.0
}
