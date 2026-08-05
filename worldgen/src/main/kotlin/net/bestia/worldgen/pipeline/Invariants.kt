package net.bestia.worldgen.pipeline

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.civ.BuildingChannels
import net.bestia.worldgen.civ.DistrictChannels
import net.bestia.worldgen.civ.GateChannels
import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.civ.WallChannels
import net.bestia.worldgen.climate.SeasonalPrecipitation
import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Order
import net.bestia.worldgen.core.Parallel
import net.bestia.worldgen.core.SiteKind
import net.bestia.worldgen.core.WorldWrap
import net.bestia.worldgen.core.StageListener
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.fields.D8
import net.bestia.worldgen.geo.ClosedBasins
import net.bestia.worldgen.history.SiteChannels
import net.bestia.worldgen.hydro.LakeChannels
import net.bestia.worldgen.karst.CaveChannels
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.worldgen.pop.BusinessChannels
import net.bestia.worldgen.pop.EconomyChannels
import net.bestia.worldgen.resource.DepositChannels
import net.bestia.worldgen.resource.MinableOre
import net.bestia.worldgen.resource.OreBody
import net.bestia.worldgen.resource.ResourceType
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureEvaluator
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.FootprintFeature
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.voxel.PropKind
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.voxel.ChunkMaterializer
import net.bestia.worldgen.voxel.Stratigraphy
import java.util.Locale
import net.bestia.worldgen.civ.SettlementSpawnPoints
import net.bestia.worldgen.mana.CorruptionStage
import net.bestia.worldgen.spawn.SpawnerChannels
import net.bestia.worldgen.spawn.VegetationStandChannels
import kotlin.math.abs
import kotlin.math.hypot

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
   * ### One world per core, a batch at a time
   *
   * This was deliberately sequential, on the grounds that reading the seed of a failure straight off the
   * output matters more than throughput. It still does, and that is why [onSeed] is called from this
   * thread in seed order: the output is identical to what the sequential version printed.
   *
   * What changed is that worlds are *built* in batches of [Parallel.threads] first. Seeds are perfectly
   * independent - a different world, a different everything - so this is the one place in the module
   * where coarse-grained parallelism is available, and it scales far better than splitting the rows of a
   * single world does. A two-hundred-seed sweep is the slowest thing a developer here waits for.
   *
   * Batched rather than all at once because [onSeed] is handed the world itself, so every world in flight
   * has to stay in memory until its turn to be reported comes. A batch holds at most one world per core;
   * at the default 192 cells that is a few megabytes each, but a sweep at `--cells 512` should expect to
   * need a heap sized for a dozen of them.
   *
   * Note that each build runs serially inside its own thread, since [Parallel.map] marks its workers as
   * being in a parallel region already. That is the right way round: twelve whole worlds at once keeps
   * every core busy for the entire sweep, where one world at a time leaves them idle through
   * priority-flood and the history simulation.
   */
  fun sweep(
    seeds: Int,
    firstSeed: Long = 1L,
    config: (Long) -> WorldConfig = { StandardWorld.demoConfig(it) },

    /**
     * The tuning every world in the sweep is built with.
     *
     * Here rather than defaulted inside the loop because judging a tuning change *is* what the sweep is for:
     * a candidate params file is accepted or rejected on what it does to the land-fraction and lake
     * distributions over a few hundred worlds. A sweep that quietly built the defaults while a file was on the
     * command line would report the wrong generator's figures, which is worse than not reading the file.
     */
    params: WorldParams = WorldParams.DEFAULT,

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
    val batch = Parallel.threads.coerceAtLeast(1)
    var done = 0

    while (done < seeds) {
      val size = minOf(batch, seeds - done)
      val offset = done

      // Checking inside the batch as well as building: the checks are several full passes over every
      // layer, and they read the world without touching it.
      val built = Parallel.map(size) { k ->
        val seed = firstSeed + offset + k
        val generated = StandardWorld.build(config(seed), StageListener.NONE, params)
        Triple(seed, Report(1, check(generated)), generated)
      }

      for ((seed, report, generated) in built) {
        violations.addAll(report.violations)
        onSeed(seed, report, generated)
      }

      done += size
    }

    return Report(seeds, violations)
  }

  /** Distance from a road or town inside which a den is "settled country". */
  private const val SETTLED_RANGE_METRES = 10_000.0

  /**
   * How close settled country's mean level may come to the whole world's before the ramp is not a ramp.
   *
   * Measured at 0.41 to 0.71 over twenty seeds, median 0.51, with the sparsest world in two hundred at 0.82.
   * See `checkSpawnersRespectCorruption` for why this is a ratio and what the absolute it replaced got wrong.
   */
  private const val SETTLED_MAX_LEVEL_RATIO = 0.85

  /** How far the corrupted share may sit from its target before it is a bug rather than a seed. */
  private const val CORRUPTED_SHARE_TOLERANCE = 0.03

  /** Most the near-town mean corruption may be, as a fraction of the mean over all land. */
  private const val CIVILISATION_SUPPRESSION_RATIO = 0.25

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
    checkOreDepositsAreSpacedApart(generated, ::fail)
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
    checkBuiltSitesAreWhereTheyClaim(generated, ::fail)
    checkCavesAreWellFormed(generated, ::fail)
    checkCanopyCoverAgreesWithTheBiome(generated, ::fail)
    checkPondsHoldWaterWithoutAWall(generated, ::fail)
    checkVolcanicBiomesStandOnVolcanoes(generated, ::fail)

    // Mana and corruption.
    checkCorruptionHitsItsTarget(generated, ::fail)
    checkCorruptionAvoidsCivilisation(generated, ::fail)
    checkWoundsAreInCorruptedGround(generated, ::fail)

    // Bestia spawners.
    checkTheWorldHasSpawners(generated, ::fail)
    checkSpawnersAreOnDryLand(generated, ::fail)
    checkSpawnersAreWellFormed(generated, ::fail)
    checkSpawnersNearHomeAreGentle(generated, ::fail)
    checkSpawnersRespectCorruption(generated, ::fail)

    // Vegetation props and the stands that look after them.
    checkTheWorldHasVegetationStands(generated, ::fail)
    checkVegetationStandsAreOnDryLand(generated, ::fail)
    checkVegetationStandsAreWellFormed(generated, ::fail)
    checkVegetationStandsAreWooded(generated, ::fail)
    checkVegetationStandsAdvertiseFillableCapacity(generated, ::fail)
    checkPropsAreWellPlaced(generated, ::fail)
    checkDistrictsHoldTheirBuildings(generated, ::fail)

    return out
  }

  /**
   * A vector pond is water the terrain can actually hold.
   *
   * Three claims, and the third is the one this was written for.
   *
   * - **A pond is level and above its own bed.** Cheap, and it catches a producer that has written the two
   *   channels the wrong way round.
   * - **A pond stands on land.** `PondStage` gates on this at the centroid; here it is re-checked so that
   *   the gate failing open would be visible rather than silent.
   * - **A pond has no wall of water at its shore.** This is the failure areal water invites and no other
   *   tier has: the pond's extent is the *ring*, so a column just outside it gets no water however low the
   *   ground there is. If the ring is drawn inside the waterline, the result is a step of standing water
   *   with dry ground beside it - measured at up to thirty metres on the first attempt, which sized the ring
   *   from the trough's floor rather than from where its wall crosses the water surface. Sampling just
   *   outside the ring and requiring the ground there to be at or near the surface is what says the ring is
   *   in the right place, and it is a claim about the *finished* terrain rather than about the trough
   *   profile the producer solved against - so detail noise and every other feature are included.
   */
  /**
   * A district contains the buildings it was grown from.
   *
   * The one claim worth checking, and it is not trivially true: the ring is a convex hull of building corners
   * pushed outward, then *simplified* to fit `Ring.MAX_VERTICES`, and simplification cuts corners off. A
   * district of two hundred buildings loses well over a hundred vertices, and each one it loses moves the
   * boundary inward across ground that had a building on it. If that ever bites, this is where it shows.
   *
   * Stated as "at least as many as it claims", against the count the producer stored, because the hull may
   * legitimately contain buildings of *other* quarters that interleave with this one.
   */
  private fun checkDistrictsHoldTheirBuildings(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val districts = generated.world.features.all()
      .filter { it.kind == FeatureKind.DISTRICT }
      .filterIsInstance<AreaFeature>()
    if (districts.isEmpty()) return

    var checked = 0
    for (district in districts) {
      val claimed = district.attribute(DistrictChannels.BUILDINGS).toInt()
      val inside = generated.world.features.query(district.bbox)
        .count { it.kind == FeatureKind.BUILDING && district.contains(it.bbox.centerX, it.bbox.centerY) }

      if (inside < claimed) {
        fail(
          "districts hold their buildings",
          "$district was grown from $claimed buildings and its ring contains $inside"
        )
        return
      }
      checked++
    }
    require(checked == districts.size)
  }

  private fun checkPondsHoldWaterWithoutAWall(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val ponds = generated.world.features.all()
      .filter { it.kind == FeatureKind.LAKE }
      .filterIsInstance<AreaFeature>()
    if (ponds.isEmpty()) return

    val seaLevel = generated.config.seaLevel

    for (pond in ponds) {
      val surface = runCatching { pond.attribute(LakeChannels.SURFACE_ELEVATION) }.getOrNull()
      if (surface == null) {
        fail("pond channels", "$pond carries no ${LakeChannels.SURFACE_ELEVATION}")
        continue
      }
      val floor = pond.attribute(LakeChannels.FLOOR_ELEVATION)
      if (floor >= surface) {
        fail("pond stands above its bed", "$pond has a bed at $floor under a surface at $surface")
      }
      if (surface <= seaLevel) {
        fail("pond is on land", "$pond stands at $surface, at or below sea level $seaLevel")
      }

      // Just outside the ring, along the outward normal at every vertex. The ground there is the *finished*
      // heightfield - base surface plus every vector feature in (priority, id) order - which is what a
      // player stands on and is what the producer, solving against the trough profile alone, cannot see.
      //
      // One evaluator per pond, built the same way `ChunkHeightSampler` builds one per chunk. Going through
      // `generated.columns` instead would mean materialising a thousand columns to read two dozen.
      val neighbours = generated.world.features.query(pond.bbox.expanded(SHORE_PROBE * 2))
      val evaluator = FeatureEvaluator(neighbours)

      // Earthworks that arrive *after* the pond does. `PondStage` runs before settlement and town, so a
      // road cut or a graded building platform beside a lake is ground the producer could not have seen
      // when it chose the water level - and lowering ground next to standing water is what those features
      // are for. Two ponds in six hundred on a 200-seed sweep at 256 cells, and it is the civil engineering
      // that is questionable there, not the lake. Skipping the probe rather than widening the tolerance
      // keeps the claim exact everywhere else.
      val earthworks = neighbours.filter { it.kind in RESHAPES_THE_GROUND }

      var worst = 0.0
      var worstAt = Vec2d.ZERO
      for (i in 0 until pond.ring.vertexCount) {
        val vertex = pond.ring.vertex(i)

        // The *local* outward normal, averaged over the two edges meeting at this vertex, not a radial from
        // the centroid. On a long thin pond a radial direction near the caps points along the shore rather
        // than across it, so the probe walks twenty metres down the lake and reports the water it finds
        // there as a wall. The ring is counter-clockwise, so an edge's outward normal is its tangent turned
        // the other way.
        val before = outwardOf(pond.ring.vertex(i - 1), vertex)
        val after = outwardOf(vertex, pond.ring.vertex(i + 1))
        val outward = (before + after).normalized()
        if (outward.lengthSquared == 0.0) continue
        val outside = vertex + outward * SHORE_PROBE
        if (pond.ring.contains(outside)) continue
        if (earthworks.any { it.bbox.contains(outside.x, outside.y) }) continue

        val ground = evaluator.heightAt(outside.x, outside.y, generated.base.heightAt(outside.x, outside.y))
        val below = surface - ground
        if (below > worst) {
          worst = below
          worstAt = outside
        }
      }

      if (worst > MAX_SHORE_WALL) {
        fail(
          "pond shore is not a wall",
          "$pond stands at $surface with ground ${"%.1f".format(Locale.ROOT, worst)} m below it " +
              "just outside its ring at (${worstAt.x.toInt()}, ${worstAt.y.toInt()})"
        )
      }
    }
  }

  /**
   * The canopy raster says what the biome map says, and says *something*.
   *
   * Three claims, separately named so a failure points at one of them.
   *
   * - **No canopy over open ocean.** Tested only on cells whose eight neighbours are also sea, because the
   *   classifier's boundary warp displaces a lookup by up to 420 m and can legitimately reach one cell inland
   *   from a coastal water cell. An interior ocean cell has no such excuse, and cover there would mean the
   *   biome term is not being consulted at all.
   * - **Forests are wooded.** The one claim that would fail if the stage ran and produced nothing, which is
   *   the failure mode this module has shipped three times: a subsystem that is complete, tested, and never
   *   reached looks exactly like one that works. A bound rather than a value, because the density is tuned by
   *   measurement and re-tuning it must not break this.
   * - **Grassland is not a forest.** The check that catches the specific mistake this stage was written
   *   around: `Biome.litter` puts grassland at four fifths of a temperate forest, and using it as the
   *   vegetation term - which is what the plan for this phase said to do - plants a wood on every prairie.
   *   Comparative as well as absolute, so a world-wide density change moves both sides together.
   */
  private fun checkCanopyCoverAgreesWithTheBiome(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val cover = generated.world.layers[LayerId.CANOPY_COVER] as? FloatLayer ?: return
    val biome = generated.world.layers[LayerId.BIOME] as? IntLayer ?: return
    val region = cover.region

    var forestSum = 0.0
    var forestCells = 0
    var openSum = 0.0
    var openCells = 0

    fun biomeAt(x: Int, y: Int) =
      Biome.entries.getOrNull(biome[region.minX + x, region.minY + y])

    for (y in 0 until region.height) {
      for (x in 0 until region.width) {
        val here = biomeAt(x, y) ?: continue
        val value = cover[region.minX + x, region.minY + y].toDouble()

        when (here) {
          Biome.OCEAN -> {
            if (value > 0.0 && surroundedBySea(::biomeAt, region, x, y)) {
              fail(
                "canopy cover agrees with the biome",
                "cover $value on an ocean cell at ($x,$y) with nothing but sea around it"
              )
              return
            }
          }

          Biome.TEMPERATE_FOREST, Biome.TEMPERATE_RAINFOREST, Biome.TAIGA,
          Biome.TROPICAL_RAINFOREST, Biome.TROPICAL_SEASONAL_FOREST -> {
            forestSum += value
            forestCells++
          }

          Biome.GRASSLAND, Biome.STEPPE -> {
            openSum += value
            openCells++
          }

          else -> Unit
        }
      }
    }

    if (forestCells >= MIN_CELLS_TO_JUDGE) {
      val mean = forestSum / forestCells
      if (mean < MIN_FOREST_COVER) {
        fail(
          "canopy cover agrees with the biome",
          "forest biomes average $mean canopy over $forestCells cells, under $MIN_FOREST_COVER"
        )
        return
      }

      if (openCells >= MIN_CELLS_TO_JUDGE) {
        val open = openSum / openCells
        if (open >= mean * MAX_OPEN_SHARE_OF_FOREST) {
          fail(
            "canopy cover agrees with the biome",
            "grassland and steppe average $open canopy against forest's $mean, which is not a distinction"
          )
        }
      }
    }
  }

  /** Whether every one of a cell's eight neighbours is also ocean. Edge cells count as surrounded. */
  private fun surroundedBySea(
    biomeAt: (Int, Int) -> Biome?,
    region: CellRegion,
    x: Int,
    y: Int
  ): Boolean {
    for (dy in -1..1) {
      for (dx in -1..1) {
        val nx = x + dx
        val ny = y + dy
        if (nx < 0 || ny < 0 || nx >= region.width || ny >= region.height) continue
        if (biomeAt(nx, ny) != Biome.OCEAN) return false
      }
    }
    return true
  }

  /**
   * The four built sites stand on dry land, name what they are built on, and keep their distance.
   *
   * Four separate claims, and each one is a different way for the placement scan to be wrong:
   *
   * - **Nothing is founded in water.** The scans all test elevation against sea level, so this catches the test
   *   being dropped or applied to the wrong layer - and a keep in the sea is the most visible possible failure.
   * - **A mine names a real deposit.** Mines are placed *at* an `ORE_DEPOSIT` marker, so the join is positional
   *   and checkable. A mine with no deposit under it is a hole in the ground, and it is exactly what would
   *   happen if the candidate position drifted from the marker it came from.
   * - **A lighthouse is coastal.** Its whole purpose is guarding an approach, and the check is against
   *   `DISTANCE_TO_OCEAN` - the field that had no reader at all before this phase.
   * - **A lighthouse and a monastery are clear of any settlement.** Both are defined by *not* being in a town:
   *   a light inside a town is a lamp, and a monastery on the best farmland is a manor.
   *
   * Not a check that these sites exist. Whether a civilisation ever builds one depends on a thousand years of
   * technology and war, so a world can legitimately have none - and `history/SpecialSitesTest` pins existence to
   * a seed that does, rather than making this vacuous on the ones that do not.
   */
  private fun checkBuiltSitesAreWhereTheyClaim(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val elevation = generated.world.layers[LayerId.ELEVATION] as? FloatLayer ?: return
    val distanceToOcean = generated.world.layers[LayerId.DISTANCE_TO_OCEAN] as? FloatLayer ?: return
    val seaLevel = generated.config.seaLevel

    val built = setOf(
      FeatureKind.MINE, FeatureKind.MONASTERY, FeatureKind.FORT, FeatureKind.LIGHTHOUSE, FeatureKind.SHRINE
    )

    val all = generated.world.features.all()
    val deposits = all.filter { it.kind == FeatureKind.ORE_DEPOSIT }.filterIsInstance<PointMarker>()
    val settlements = all.filter { it.kind == FeatureKind.SETTLEMENT }.filterIsInstance<PointMarker>()

    for (feature in all) {
      if (feature.kind !in built) continue
      val marker = feature as? PointMarker ?: continue
      val at = marker.position

      if (elevation.sampleBilinear(at.x, at.y) <= seaLevel) {
        fail("built sites are where they claim", "${feature.kind} ${feature.id} stands in water")
        return
      }

      when (feature.kind) {
        FeatureKind.MINE -> {
          val named = deposits.any { it.position.distanceTo(at) <= MINE_DEPOSIT_TOLERANCE }
          if (!named) {
            fail(
              "built sites are where they claim",
              "mine ${feature.id} has no ore deposit within ${MINE_DEPOSIT_TOLERANCE.toInt()} m of it"
            )
            return
          }
        }

        FeatureKind.LIGHTHOUSE -> {
          if (distanceToOcean.sampleBilinear(at.x, at.y) > LIGHTHOUSE_COAST_TOLERANCE) {
            fail("built sites are where they claim", "lighthouse ${feature.id} is not on the coast")
            return
          }
          if (settlements.any { it.position.distanceTo(at) < BUILT_SITE_CLEARANCE }) {
            fail("built sites are where they claim", "lighthouse ${feature.id} stands inside a settlement")
            return
          }
        }

        FeatureKind.MONASTERY -> {
          if (settlements.any { it.position.distanceTo(at) < BUILT_SITE_CLEARANCE }) {
            fail("built sites are where they claim", "monastery ${feature.id} stands inside a settlement")
            return
          }
        }

        /*
         * A shrine is clear of any settlement, and it **names an Order**.
         *
         * The second half is the one worth asserting on every seed. A shrine's Order is a station channel rather
         * than a `FeatureKind`, which is the trade `SiteKind.SHRINE` documents taking - and the cost of that
         * trade is exactly this: nothing in the type system stops an unset channel reaching the materialiser,
         * where `TownStructures.shrineColumn` would quietly build a Chaos cairn for it. A `-1` here means a
         * player is looking at the wrong Order's monument, which is a lore bug no map or section view would show.
         */
        FeatureKind.SHRINE -> {
          if (settlements.any { it.position.distanceTo(at) < BUILT_SITE_CLEARANCE }) {
            fail("built sites are where they claim", "shrine ${feature.id} stands inside a settlement")
            return
          }
          val order = runCatching { marker.attribute(SiteChannels.ORDER).toInt() }.getOrNull()
          if (order == null || order !in Order.entries.indices) {
            fail(
              "built sites are where they claim",
              "shrine ${feature.id} names no Order (${SiteChannels.ORDER} = $order), so it would " +
                  "materialise as whichever structure the fallback happens to be"
            )
            return
          }
        }

        else -> Unit
      }
    }
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

      /*
       * A sworn people has an oath in its span, and an unsworn one has no oath year at all.
       *
       * Both halves catch a real shape of mistake rather than an impossible one. `sworn` and `swornYear` are two
       * fields that have to move together, set in two places - the first oath and a schism - so the failure mode
       * is one being written without the other, which downstream reads as a people who have believed something
       * since the year zero.
       */
      if (civ.sworn == null && civ.swornYear != 0) {
        fail(
          "history is self consistent",
          "civ ${civ.index} holds no Order but swore in ${civ.swornYear}"
        )
        return
      }
      if (civ.sworn != null && civ.swornYear !in chronicle.span) {
        fail(
          "history is self consistent",
          "civ ${civ.index} swore to ${civ.sworn} in ${civ.swornYear}, outside ${chronicle.span}"
        )
        return
      }
    }

    /*
     * Every shrine names an Order, belongs to a civ, and went up while that civ held that Order.
     *
     * The last clause is the one that needs a check rather than a type: a shrine records the Order that raised
     * it and the civ records only the Order it holds *now*, so after a schism the two legitimately disagree -
     * which means the assertion cannot be "they match". What it can be is that the shrine's Order was one this
     * civ ever actually held, and the only record of that is the log. A shrine for an Order its founder never
     * swore to is a monument with no reason, and it is what a wrong `order` argument to `addSite` would produce.
     */
    for (site in chronicle.sitesOfKind(SiteKind.SHRINE)) {
      val order = site.order
      if (order == null) {
        fail("history is self consistent", "shrine ${site.index} names no Order")
        return
      }
      val civ = site.civ.takeIf { it in chronicle.civs.indices }
      if (civ == null) {
        fail("history is self consistent", "shrine ${site.index} belongs to no civ (${site.civ})")
        return
      }
      val everHeld = chronicle.civs[civ].sworn == order ||
          chronicle.eventsOf(Actor(ActorType.CIV, civ)).any {
            (it.kind == EventKind.ORDER_SWORN || it.kind == EventKind.ORDER_SCHISM) &&
                it.detail.contains(order.label)
          }
      if (!everHeld) {
        fail(
          "history is self consistent",
          "shrine ${site.index} was raised for ${order.shortForm} by civ $civ, which never held it"
        )
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

  /**
   * Every ruin marker corresponds to a settlement the log says was emptied, and vice versa.
   *
   * **Both residue kinds**, and the list is what makes this correct rather than a coincidence: an abandonment
   * leaves a `RUIN` unless it was an eruption, which leaves an `ASH_RUIN` instead. Counting only `RUIN` came up
   * short the moment eruptions started producing the other kind, and it came up short *quietly* on the seeds with
   * no volcano near a town - which is exactly the shape of failure that gets shipped. Any future cause that leaves
   * its own kind of residue has to be added here too.
   */
  private fun checkEveryRuinHasAnEvent(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val chronicle = generated.world.chronicle
    if (chronicle.events.isEmpty()) return

    val ruinSites = RESIDUE_KINDS.flatMap { chronicle.sitesOfKind(it) }
    for (site in ruinSites) {
      val record = chronicle.settlements.getOrNull(site.settlement)
      if (record == null || !record.isRuin) {
        fail("every ruin has an event", "${site.kind} site ${site.index} names settlement ${site.settlement}")
        return
      }
    }

    val ruinedSettlements = chronicle.settlements.count { it.isRuin }
    if (ruinSites.size != ruinedSettlements) {
      fail(
        "every ruin has an event",
        "$ruinedSettlements settlements were emptied but there are ${ruinSites.size} ruin sites " +
            "(${RESIDUE_KINDS.joinToString { "$it=" + chronicle.sitesOfKind(it).size }})"
      )
    }
  }

  /**
   * Caves are where the rock dissolves, under a roof, and enterable.
   *
   * Four claims, reported separately so a failure names which one - the four fail for entirely different
   * reasons and fixing the wrong one is a wasted afternoon.
   *
   * - **Every system has a way in.** The stage discards a candidate that grows no gallery reaching daylight,
   *   so this holds *by construction* and can therefore be asserted on every seed rather than pinned to a
   *   lucky one. If it ever fails, the acceptance rule has been softened and the world has caves nobody can
   *   reach, which is `TODO.md`'s sixth habit with a nicer excuse.
   * - **A passage is in soluble rock.** The whole claim that caves are lithology-driven rather than sprinkled,
   *   and the only way to falsify it. Checked against the *shared* `Stratigraphy`, so the stage and the
   *   materialiser cannot be tested against different rock.
   * - **A passage keeps its roof**, away from its mouth. Deliberately stated against the coarse raster with no
   *   tolerance parameter: the stage clamps against exactly that, so a violation means the clamp is gone, not
   *   that a number wants adjusting. The chunk tier's own guard against the *fine* surface cannot be checked
   *   here - it is per column and this is per station - which is what `VoxelSeamCheck` and the section view
   *   are for.
   * - **An entrance is on dry land.** A mouth under a lake is a mouth nothing may open: the carve refuses it,
   *   so a cave whose only entrance is submerged is a cave with no entrance at all.
   */
  private fun checkCavesAreWellFormed(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val all = generated.world.features.all()
    val systems = all.filter { it.kind == FeatureKind.CAVE_SYSTEM }.filterIsInstance<PointMarker>()
    if (systems.isEmpty()) return

    val elevation = generated.world.layers[LayerId.ELEVATION] as? FloatLayer ?: return
    val waterLevel = generated.world.layers[LayerId.WATER_LEVEL] as? FloatLayer
    val seaLevel = generated.config.seaLevel
    val strata = generated.materializer.strata

    val entrances = all.filter { it.kind == FeatureKind.CAVE_ENTRANCE }.filterIsInstance<PointMarker>()
    val withEntrance = HashSet<Int>()
    for (entrance in entrances) {
      val system = runCatching { entrance.attribute(CaveChannels.SYSTEM).toInt() }.getOrNull() ?: continue
      withEntrance.add(system)

      val ground = elevation.sampleBilinear(entrance.position.x, entrance.position.y)
      if (ground <= seaLevel) {
        fail("cave entrances are on dry land", "system $system opens at ${ground.toInt()} m, under the sea")
        return
      }
      val water = waterLevel?.sampleBilinear(entrance.position.x, entrance.position.y) ?: Double.NaN
      if (!water.isNaN() && water > ground) {
        fail("cave entrances are on dry land", "system $system opens under a lake standing at ${water.toInt()} m")
        return
      }
    }

    for (system in systems) {
      val index = runCatching { system.attribute(CaveChannels.SYSTEM).toInt() }.getOrNull() ?: continue
      if (index !in withEntrance) {
        fail("every cave system has an entrance", "system $index has no way in")
        return
      }
    }

    for (feature in all) {
      if (feature.kind != FeatureKind.CAVE_PASSAGE) continue
      val passage = feature as? MarkerFeature ?: continue
      val stations = passage.stations ?: continue
      val floorChannel = runCatching { stations.channel(CaveChannels.FLOOR) }.getOrNull() ?: continue
      val heightChannel = stations.channel(CaveChannels.HEIGHT)

      for (i in 0 until passage.centerline.vertexCount) {
        val at = passage.centerline.points[i]
        val floor = stations.valueAt(floorChannel, i)
        val height = stations.valueAt(heightChannel, i)

        // Sampled at the middle of the void rather than at the floor. The floor sits on a bedding plane and
        // an elevation exactly on a bed boundary belongs to whichever bed the division rounds into - which is
        // a real bug this caught once already, and would make the check itself unreliable here.
        val rock = strata.columnAt(at.x, at.y).rockAt(floor + height * 0.5)
        if (rock !in Stratigraphy.SOLUBLE) {
          fail(
            "cave passages are in soluble rock",
            "${feature.id} station $i at ${floor.toInt()} m is in $rock"
          )
          return
        }

        // Near the mouth the roof is *meant* to be gone, so the stations around it are exempt. Measured
        // against every entrance rather than only its own system's, which is conservative in the safe
        // direction: it can excuse a station it should have checked, never fail one it should not have.
        if (entrances.any { it.position.distanceTo(at) < CAVE_MOUTH_EXEMPTION }) continue

        val coarse = elevation.sampleBilinear(at.x, at.y)
        if (floor + height >= coarse) {
          fail(
            "cave passages keep their roof",
            "${feature.id} station $i has its roof at ${(floor + height).toInt()} m " +
                "under ground at ${coarse.toInt()} m"
          )
          return
        }
      }
    }
  }

  /** How close to a mouth a passage station may be before the roof claim stops applying, in metres. */
  private const val CAVE_MOUTH_EXEMPTION = 60.0

  /** Cells of one kind below which its mean says more about the sample than about the world. */
  private const val MIN_CELLS_TO_JUDGE = 200

  /**
   * Mean canopy cover a world's forest biomes must reach.
   *
   * Well under what they measure - the reference world runs several times this - because the number it
   * guards against is zero. It is the tripwire for a vegetation stage that runs and produces nothing, not a
   * pin on the tuning.
   */
  private const val MIN_FOREST_COVER = 0.06

  /** How much of a forest's canopy grassland and steppe may have before the two are not distinguishable. */
  private const val MAX_OPEN_SHARE_OF_FOREST = 0.5

  /**
   * How much more wooded a stand's own cell must be than the average over all land.
   *
   * Only just above one, because it is a tripwire rather than a pin: what it guards against is a placement
   * pass that scatters stands over any land at all, which would land them at the average by construction.
   */
  private const val STAND_COVER_MARGIN = 1.5

  /**
   * Stands whose advertised capacity is checked against the props actually emitted.
   *
   * Each one materialises the column heights of every chunk its disc covers - a 400 m radius over 32 m chunks
   * is about 625 chunks - so this is the expensive check in the file and the sample is deliberately small.
   */
  private const val CAPACITY_SAMPLES = 3

  /**
   * How far the emitted tree count may sit from what the stands advertised.
   *
   * Wide on purpose. The advertisement is an expectation over a clumped process and a handful of discs is a
   * small sample of it, so this is a guard against being wrong by a *factor* - an entity lattice moving on one
   * side only, or a capacity formula built on cover instead of density - and not against being wrong by a
   * fifth.
   */
  private const val CAPACITY_MIN_RATIO = 0.4
  private const val CAPACITY_MAX_RATIO = 2.5

  /** Chunks sampled per axis when checking prop placement. Each one is a full column-heights build. */
  private const val PROP_PLACEMENT_SAMPLES = 6

  /**
   * Metres outside a pond's ring at which the ground is sampled, looking for a wall of water.
   *
   * Far enough out that the ring's own carve has eased off - the pond's `shore` is fourteen metres - and
   * close enough in that it is still the pond's shoreline rather than the hillside behind it. It is also
   * larger than the producer's own eight-metre march step, so it is genuinely outside the last column the
   * producer looked at rather than inside it.
   */
  /** Outward normal of a counter-clockwise ring edge: its tangent turned clockwise. */
  private fun outwardOf(from: Vec2d, to: Vec2d): Vec2d {
    val edge = (to - from).normalized()
    return Vec2d(edge.y, -edge.x)
  }

  /** Kinds that cut or fill the ground and are emitted after the ponds are placed. See the shore check. */
  private val RESHAPES_THE_GROUND = setOf(
    FeatureKind.ROAD,
    FeatureKind.STREET,
    FeatureKind.SETTLEMENT_GRADING,
    FeatureKind.BRIDGE
  )

  private const val SHORE_PROBE = 14.0

  /**
   * Metres of water a pond may stand above the ground just outside its ring before it counts as a wall.
   *
   * Not zero. The ring is a polygon of two dozen vertices around a shore that the detail noise wobbles by a
   * metre or two, and the producer solves the waterline against the trough profile rather than against the
   * finished terrain, so a small mismatch is expected and harmless. What this catches is the structural
   * version: a ring sized from the wrong quantity, which put the boundary hundreds of metres inside the
   * waterline and left a step tens of metres tall.
   */
  private const val MAX_SHORE_WALL = 4.0

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
    // A new kind must be added here or it is exempt from the margin check *by accident* - this is an explicit
    // list, not "every marker with a radius", so forgetting a kind is silent. The channel is named per kind
    // because a cave mouth is not a site and carries its own; a kind that names a channel it does not have is
    // now a failure rather than a skip, which is what the `?: continue` here used to make it.
    val structural = mapOf(
      FeatureKind.RUIN to SiteChannels.RADIUS,
      FeatureKind.ASH_RUIN to SiteChannels.RADIUS,
      FeatureKind.BATTLEFIELD to SiteChannels.RADIUS,
      FeatureKind.TOMB to SiteChannels.RADIUS,
      FeatureKind.MONUMENT to SiteChannels.RADIUS,
      FeatureKind.MINE to SiteChannels.RADIUS,
      FeatureKind.MONASTERY to SiteChannels.RADIUS,
      FeatureKind.FORT to SiteChannels.RADIUS,
      FeatureKind.LIGHTHOUSE to SiteChannels.RADIUS,
      FeatureKind.CAVE_HOARD to SiteChannels.RADIUS,
      // The widest structural marker there is, at 260 m against a 320 m margin - so this is the one entry in
      // this map that is anywhere near binding, and the one that would actually catch somebody widening a site.
      FeatureKind.WOUND to SiteChannels.RADIUS,
      FeatureKind.CAVE_ENTRANCE to CaveChannels.MOUTH
    )

    for (feature in generated.world.features.all()) {
      val channel = structural[feature.kind] ?: continue
      val marker = feature as? PointMarker ?: continue
      val radius = runCatching { marker.attribute(channel) }.getOrNull()
      if (radius == null) {
        fail(
          "structural markers fit the query margin",
          "${feature.kind} ${feature.id} carries no '$channel' channel, so its reach cannot be checked"
        )
        return
      }

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
   * A roadside inn is beside a road and not inside a **standing** settlement.
   *
   * The whole point of one: it exists because there is nowhere else to stop. An inn that ended up inside a
   * town is one the town already had, counted twice.
   *
   * ### Standing, and it used to be every settlement site
   *
   * `EconomyStage.roadsideInns` skips near an `EconomyReader.Place`, and that reader joins `SETTLEMENT` to
   * `SETTLEMENT_HISTORY` and drops anything never founded, emptied, or down to no people. This check compared
   * against every `SETTLEMENT` marker on the map instead - so it asserted something the producer never
   * promised, and asserted the *worse* rule besides: an inn beside a ruin is not a duplicate of anything, it is
   * good worldbuilding, and a site nobody ever settled is a patch of grass.
   *
   * It went unnoticed because a false positive needs an inn to land within 150 m of a site that is not there.
   * A 200-seed sweep at 256 cells found exactly one, at 149 m, once history started emptying more towns. The
   * same mismatch - producer measuring one thing and invariant another - is on record in `SpawnerStage`'s home
   * ring, where the stage measured from the settlement centre and the check from the arrival point.
   */
  private fun checkRoadsideInnsAreOnTheRoad(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val inns = generated.world.features.all()
      .filter { it.kind == FeatureKind.ROADSIDE_INN }
      .filterIsInstance<PointMarker>()
    if (inns.isEmpty()) return

    val chronicle = generated.world.chronicle
    val sites = generated.world.features.all()
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<PointMarker>()
      .filter { marker ->
        val index = runCatching { marker.attribute(SettlementChannels.INDEX).toInt() }.getOrNull()
        // No chronicle means no history stage ran, and then every placed site is as good as standing.
        index == null || chronicle.settlements.isEmpty() ||
            chronicle.settlementStood(index, chronicle.presentYear)
      }
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
          "inn ${inn.id} is only ${nearestSettlement.toInt()} m from a standing settlement"
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

  /**
   * Deposits carry a usable type, a positive extent, a richness in range, and an honest tonnage.
   *
   * The tonnage clause is the one worth having. A deposit advertises how much metal is in it, the chunk
   * tier decides voxel by voxel where that metal actually is, and the two agree only because both go
   * through [OreBody]. This is what would notice if one of them stopped.
   */
  private fun checkDepositsAreWellFormed(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val mean = generated.params.resource.grades.meanYieldKg

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

      val radius = deposit.attribute(DepositChannels.RADIUS)
      if (radius <= 0.0) {
        fail("deposits are well formed", "${feature.id} has no extent")
        return
      }

      val tons = deposit.attribute(DepositChannels.TONS)
      if (!(tons > 0.0)) {
        fail("deposits are well formed", "${feature.id} holds $tons tons")
        return
      }

      // The one assertion that keeps the two tiers honest. The world tier picked `radius` so that a body of
      // that size at that concentration holds the advertised tonnage; the chunk tier fills the same shape
      // with the same probability. If those ever drift apart, a deposit claims metal that is not there and
      // nothing else in the system would notice.
      if (MinableOre.of(ResourceType.entries[type]) == null) continue

      val modelled = OreBody.tonsOf(radius, richness, generated.config.voxelSize, mean)
      if (abs(modelled - tons) > tons * TONNAGE_TOLERANCE) {
        fail(
          "deposits are well formed",
          "${feature.id} claims ${tons.toInt()} t but its ${radius.toInt()} m body at richness " +
              "${"%.3f".format(richness)} models ${modelled.toInt()} t"
        )
        return
      }
    }
  }

  /**
   * No two mineable deposits sit on top of each other.
   *
   * Two rules in one, and they fail differently. The **separation** rule is the gameplay one: every resource
   * samples its own Poisson set, so before `ResourceStage` gained a dispersal pass a copper body and a silver
   * body could land a hundred metres apart, and a player who found one had found both. The **overlap** rule is
   * the physical one: two intersecting bodies would make one voxel two different ores, and `OreVeins` would
   * settle it by feature order rather than by geology.
   *
   * Checked with a bucket grid rather than pairwise, because a full world holds tens of thousands of deposits
   * and the quadratic version of this took long enough to be worth not writing.
   */
  private fun checkOreDepositsAreSpacedApart(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val separation = generated.params.resource.oreSeparation
    val clearance = generated.params.resource.bodyClearance

    val deposits = generated.world.features.all()
      .filter { it.kind == FeatureKind.ORE_DEPOSIT }
      .filterIsInstance<PointMarker>()
      .filter { marker ->
        val type = runCatching { marker.attribute(DepositChannels.TYPE).toInt() }.getOrNull() ?: return@filter false
        type in ResourceType.entries.indices && MinableOre.of(ResourceType.entries[type]) != null
      }

    val buckets = HashMap<Long, MutableList<PointMarker>>()
    for (deposit in deposits) {
      val cx = Math.floor(deposit.position.x / separation).toInt()
      val cy = Math.floor(deposit.position.y / separation).toInt()

      for (dy in -1..1) {
        for (dx in -1..1) {
          for (other in buckets[bucketKey(cx + dx, cy + dy)] ?: continue) {
            val distance = deposit.position.distanceTo(other.position)
            if (distance < separation * (1.0 - SEPARATION_TOLERANCE)) {
              fail(
                "ore deposits are spaced apart",
                "${deposit.id} and ${other.id} are ${distance.toInt()} m apart, " +
                    "needing ${separation.toInt()} m"
              )
              return
            }

            val bodies = deposit.attribute(DepositChannels.RADIUS) + other.attribute(DepositChannels.RADIUS)
            if (distance < bodies * clearance * (1.0 - SEPARATION_TOLERANCE)) {
              fail(
                "ore deposits are spaced apart",
                "orebodies ${deposit.id} and ${other.id} are ${distance.toInt()} m apart, " +
                    "which their combined ${bodies.toInt()} m of radius overlaps"
              )
              return
            }
          }
        }
      }

      buckets.getOrPut(bucketKey(cx, cy)) { ArrayList() }.add(deposit)
    }
  }

  private fun bucketKey(cx: Int, cy: Int) = (cx.toLong() shl 32) xor (cy.toLong() and 0xFFFF_FFFFL)

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
  /**
   * Share of the world's **standable land** that is corrupted, or 0 on a world without the layer.
   *
   * Public and beside [landFraction] for the reason that one is: the sweep prints it per seed and the
   * invariant asserts against it, and two measurements of the same quantity would eventually disagree about
   * whether the target is being hit.
   *
   * "Standable" is dry ground clear of lakes - the same denominator `CorruptionStage` solves its threshold
   * over. Measuring against all cells instead would make the reported share swing with the ocean fraction,
   * which is 0.05 to 0.85 across legitimate seeds and has nothing to do with corruption.
   */
  fun corruptedLandShare(generated: GeneratedWorld): Double {
    val corruption = generated.world.layers[LayerId.CORRUPTION] as? FloatLayer ?: return 0.0
    val elevation = generated.world.layers[LayerId.ELEVATION] as? FloatLayer ?: return 0.0
    val waterLevel = generated.world.layers[LayerId.WATER_LEVEL] as? FloatLayer ?: return 0.0
    val seaLevel = generated.config.seaLevel

    var land = 0
    var corrupted = 0
    for (i in corruption.data.indices) {
      if (elevation.data[i] <= seaLevel) continue
      if (!waterLevel.data[i].isNaN()) continue
      land++
      if (corruption.data[i] >= CorruptionStage.CORRUPTED) corrupted++
    }

    return if (land == 0) 0.0 else corrupted.toDouble() / land
  }

  /**
   * The mana's mark on the chronicle: wounds, and the four kinds of event they set off.
   *
   * Five numbers - `wounds, blights, wards, forsaken, seersLost` - printed per seed by the sweep, and it is the
   * *counts* rather than any single check that make this subsystem impossible to ship dead. Each of the four
   * can legitimately be zero on one world (a seed can go a thousand years without a town giving up), and each
   * being zero across a whole sweep is a bug. A per-world assertion can only ever be the weaker of those two.
   *
   * The order is the causal order, which is also the order they can fail in: no wound means no blight, no
   * blight means no ward and nothing forsaken.
   */
  fun manaHistoryCensus(generated: GeneratedWorld): IntArray {
    val chronicle = generated.world.chronicle
    return intArrayOf(
      chronicle.sitesOfKind(SiteKind.WOUND).size,
      chronicle.events.count { it.kind == EventKind.BLIGHT_SPREAD },
      chronicle.events.count { it.kind == EventKind.WARD_RAISED },
      chronicle.events.count { it.kind == EventKind.SETTLEMENT_FORSAKEN },
      chronicle.events.count { it.kind == EventKind.SEER_VANISHED }
    )
  }

  /**
   * Spawners per level band: `1..8`, `9..40`, `41..79`, `80..100`.
   *
   * Four numbers rather than a mean, because a mean cannot tell "the whole world is level forty" from "half
   * of it is level one and half is level eighty", and those are a working world and a broken one. The sweep
   * prints this per seed; `checkSpawnersRespectCorruption` asserts the two ends of it.
   */
  fun spawnerCensus(generated: GeneratedWorld): IntArray {
    val bands = IntArray(4)
    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.BESTIA_SPAWN) continue
      val marker = feature as? PointMarker ?: continue
      val level = marker.attribute(SpawnerChannels.LEVEL_MAX).toInt()
      val band = when {
        level <= 8 -> 0
        level <= 40 -> 1
        level <= 79 -> 2
        else -> 3
      }
      bands[band]++
    }
    return bands
  }

  fun landFraction(generated: GeneratedWorld, layerId: LayerId = LayerId.ELEVATION): Double {
    val elevation = generated.world.layers.require<FloatLayer>(layerId)
    val seaLevel = generated.config.seaLevel
    return elevation.data.count { it > seaLevel }.toDouble() / elevation.data.size
  }

  /**
   * The mean of a raster over dry land only, or 0 on a world with no land.
   *
   * Over land rather than over everything, because the layers this is asked of are properties of ground -
   * canopy cover being the first - and averaging in the ocean answers a different question badly: a forested
   * world with a lot of sea would report a fifth of its real cover, and the figure would move when the land
   * fraction moved rather than when the thing being measured did.
   *
   * Public and shared for the reason [landFraction] is: the viewer and the sweep both print it.
   */
  fun meanOverLand(generated: GeneratedWorld, layerId: LayerId): Double {
    val layer = generated.world.layers[layerId] as? FloatLayer ?: return 0.0
    val elevation = generated.world.layers[LayerId.ELEVATION] as? FloatLayer ?: return 0.0
    val seaLevel = generated.config.seaLevel

    var sum = 0.0
    var land = 0
    for (i in layer.data.indices) {
      if (elevation.data[i] <= seaLevel) continue
      sum += layer.data[i]
      land++
    }

    return if (land == 0) 0.0 else sum / land
  }

  /**
   * The share of dry land classified as any of [kinds].
   *
   * Public and shared for [meanOverLand]'s reason: the sweep prints it and
   * [checkVolcanicBiomesStandOnVolcanoes] bounds it, and a figure two readers measure differently is one neither
   * can be trusted about.
   */
  fun landShareOfBiomes(generated: GeneratedWorld, vararg kinds: Biome): Double {
    val biome = generated.world.layers[LayerId.BIOME] as? IntLayer ?: return 0.0
    val elevation = generated.world.layers[LayerId.ELEVATION] as? FloatLayer ?: return 0.0
    val seaLevel = generated.config.seaLevel

    val wanted = kinds.toSet()
    var matched = 0
    var land = 0
    for (i in biome.data.indices) {
      if (elevation.data[i] <= seaLevel) continue
      land++
      if (Biome.entries[biome.data[i]] in wanted) matched++
    }

    return if (land == 0) 0.0 else matched.toDouble() / land
  }

  /**
   * The share of dry land within [metres] of any of [points].
   *
   * A **reach** rather than a count, and the difference is the whole reason this exists. "The world has forty
   * cave systems" is a fact about the map; "one land cell in twenty is within an hour's walk of a way in" is a
   * fact about what a player experiences, and those two come apart completely when the things being counted
   * cluster - which caves do, because they follow the limestone.
   *
   * Public and shared for the reason [landFraction] is: the sweep and the viewer both print it, and a figure
   * two tools measure differently is one neither can be trusted about.
   *
   * Measured on the coarse grid, so at a kilometre a cell it is exact to about that. That is the right
   * precision for a question posed in kilometres, and a metre-accurate answer would cost a distance transform
   * over sixteen million cells to say the same thing.
   */
  fun landShareNear(
    generated: GeneratedWorld,
    elevation: FloatLayer,
    points: List<Vec2d>,
    metres: Double
  ): Double {
    if (points.isEmpty()) return 0.0

    val config = generated.config
    val cell = config.baseResolution.metresPerCell
    val seaLevel = config.seaLevel
    val radius = metres * metres

    // Bucketed by a grid of the search radius, so each land cell tests the points in the nine buckets around
    // it rather than all of them. A world with a few thousand entrances and sixteen million cells is otherwise
    // a hundred billion distance tests.
    val bucket = HashMap<Long, MutableList<Vec2d>>()
    for (point in points) {
      val key = bucketKey(point.x, point.y, metres)
      bucket.getOrPut(key) { ArrayList() }.add(point)
    }

    var land = 0
    var near = 0
    for (cellY in 0 until config.heightCells) {
      for (cellX in 0 until config.widthCells) {
        val x = (cellX + 0.5) * cell
        val y = (cellY + 0.5) * cell
        if (elevation.sampleBilinear(x, y) <= seaLevel) continue
        land++

        val bx = Math.floorDiv(x.toLong(), metres.toLong())
        val by = Math.floorDiv(y.toLong(), metres.toLong())
        var found = false
        for (dy in -1..1) {
          for (dx in -1..1) {
            val list = bucket[(bx + dx) * BUCKET_STRIDE + (by + dy)] ?: continue
            for (point in list) {
              val ex = point.x - x
              val ey = point.y - y
              if (ex * ex + ey * ey <= radius) {
                found = true
                break
              }
            }
            if (found) break
          }
          if (found) break
        }
        if (found) near++
      }
    }

    return if (land == 0) 0.0 else near.toDouble() / land
  }

  private fun bucketKey(x: Double, y: Double, metres: Double): Long =
    Math.floorDiv(x.toLong(), metres.toLong()) * BUCKET_STRIDE + Math.floorDiv(y.toLong(), metres.toLong())

  /** Wide enough that a world 2 000 buckets across cannot alias one row onto the next. */
  private const val BUCKET_STRIDE = 1_000_003L

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
        val isWaterBiome = Biome.entries[biome[x, y]].isWater
        val hasWater = !water[x, y].isNaN()
        if (isWaterBiome != hasWater) {
          mismatches++
          if (example.isEmpty()) {
            example = "($x,$y) biome=${Biome.entries[biome[x, y]]} waterLevel=${water[x, y]}"
          }
        }
      }
    }

    if (mismatches > 0) {
      fail("water biome matches water level", "$mismatches cells disagree, e.g. $example")
    }
  }

  /**
   * Every volcanic cell is within an edifice of an actual vent, and there are not too many of them.
   *
   * The structural half is the one that cannot be satisfied by accident. `VOLCANIC_FIELD` and `GEOTHERMAL_BASIN`
   * are placed from a distance transform over the vent markers, so a cell carrying one and standing fifty
   * kilometres from the nearest vent would mean the transform, the marker set or the biome ordinals had come
   * apart - and none of those failures is visible on a map, because a patch of volcanic ground in the wrong
   * province looks exactly like a patch of volcanic ground in the right one.
   *
   * ### The share is bounded against the world's own size, not against a flat number
   *
   * `BiomeParams.volcanicVentRange` is a raw 5 km because an edifice is 10-30 km across on any world, while
   * `VolcanismParams.arcVentSpacing` *is* scaled - so every world gets about two dozen vents and the share they
   * cover falls quadratically with the world's edge. A flat bound would either pass vacuously at genesis scale or
   * fail on every test world. So the bound is expressed as "no more than this many vents' worth of edifice",
   * which is the quantity that is actually invariant, and it catches the failure that matters: a range that
   * scaled by mistake, or a rung that matched everything.
   */
  private fun checkVolcanicBiomesStandOnVolcanoes(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val biome = generated.world.layers.require<IntLayer>(LayerId.BIOME)
    val region = biome.region
    val metres = region.resolution.metresPerCell

    val vents = generated.world.features.all()
      .filter { it.kind == FeatureKind.VOLCANIC_VENT }
      .filterIsInstance<PointMarker>()
      .map { it.position }

    // The range the stage was actually built with, not a copy of the default - a params file that widened it
    // would otherwise fail here for having been obeyed.
    val range = generated.params.biome.volcanicVentRange

    var volcanic = 0
    var stranded = 0
    var example = ""

    for (y in region.minY..region.maxY) {
      for (x in region.minX..region.maxX) {
        val kind = Biome.entries[biome[x, y]]
        if (kind != Biome.VOLCANIC_FIELD && kind != Biome.GEOTHERMAL_BASIN) continue
        volcanic++

        // Cell centre against the nearest vent. One cell of slack because the placement measures cell-centre to
        // *cell containing a vent*, so a cell on the rim can be up to a diagonal further from the vent itself.
        val cx = (x + 0.5) * metres
        val cy = (y + 0.5) * metres
        val nearest = vents.minOfOrNull { hypot(it.x - cx, it.y - cy) } ?: Double.MAX_VALUE

        if (nearest > range + metres * 1.5) {
          stranded++
          if (example.isEmpty()) {
            example = "($x,$y) is $kind but the nearest of ${vents.size} vents is ${nearest.toInt()} m away"
          }
        }
      }
    }

    if (stranded > 0) {
      fail("volcanic biomes stand on volcanoes", "$stranded stranded cells, e.g. $example")
    }

    if (vents.isEmpty()) {
      // A world with no convergent boundary and no hotspot on land is legitimate, and then there must be no
      // volcanic ground at all. Stated rather than skipped, because "no vents" is also what a broken vent
      // emitter looks like and this is the one place the pair can be checked against each other.
      if (volcanic > 0) fail("volcanic biomes need a vent", "$volcanic volcanic cells and no vents at all")
      return
    }

    // Area of one edifice in cells, times a generous allowance for vents whose discs overlap being counted once
    // and for the transform's cell quantisation rounding outward.
    val perVent = Math.PI * (range / metres) * (range / metres)
    val bound = vents.size * perVent * VOLCANIC_AREA_SLACK

    if (volcanic > bound) {
      fail(
        "volcanic ground fits its vents",
        "$volcanic volcanic cells against ${bound.toInt()} for ${vents.size} vents at ${range.toInt()} m"
      )
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
      LayerId.BIOME_CONFIDENCE, LayerId.PRECIPITATION_SEASONALITY, LayerId.CANOPY_COVER,
      LayerId.MANA_DENSITY
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
   * Metres a mine may sit from the deposit it works.
   *
   * A mine is placed *at* its deposit, so the honest tolerance is zero - one coarse cell of slack is here only
   * because the position travels through a bilinear-sampled candidate and back, not because a mine is allowed to
   * wander. If this ever needs raising, the placement has drifted.
   */
  private const val MINE_DEPOSIT_TOLERANCE = 1_200.0

  /** Metres from open water a lighthouse may stand. Its own placement gate is tighter; this is the tripwire. */
  private const val LIGHTHOUSE_COAST_TOLERANCE = 5_000.0

  /** Metres a lighthouse or a monastery must keep from any settlement, which is what makes it not part of one. */
  private const val BUILT_SITE_CLEARANCE = 3_000.0

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

  /**
   * How far a deposit's advertised tonnage may sit from what its geometry models, as a fraction.
   *
   * Small because the two are the same closed form evaluated twice - anything beyond rounding means one
   * side changed and the other did not.
   */
  private const val TONNAGE_TOLERANCE = 0.001

  /** People a settlement's sector counts may miss by, from truncating seven shares. */
  private const val EMPLOYMENT_TOLERANCE = 8

  /**
   * How far over the sum of its vents' disc areas the volcanic ground may come.
   *
   * Generous, and it has to be. The bound counts each vent's edifice separately while overlapping discs are one
   * patch of ground, the distance transform quantises to whole cells and therefore rounds outward, and the
   * `GEOTHERMAL_BASIN` rung's wetness gate cuts the annulus by an amount that varies with the terrain. So this
   * catches a range that scaled by mistake or a rung matching everything - an order-of-magnitude failure - and
   * deliberately not a tuning drift, which is what the measurements in `BiomeParams` are for.
   */
  private const val VOLCANIC_AREA_SLACK = 1.6

  /**
   * The site kinds an abandoned settlement can leave behind.
   *
   * One per cause that leaves something walkable: a razed or emptied town leaves a `RUIN`, an eruption leaves an
   * `ASH_RUIN`. Stated as a list rather than derived, because `SiteKind` also holds six kinds that are *built* and
   * one that is a landform, and "every residue names a ruined settlement" is false of all of those.
   */
  private val RESIDUE_KINDS = listOf(SiteKind.RUIN, SiteKind.ASH_RUIN)

  /** Metres a roadside inn may sit from the centreline of the road it serves. */
  private const val INN_ROAD_TOLERANCE = 50.0

  /**
   * Metres an inn must keep from any settlement.
   *
   * Slack against `EconomyParams.innClearance`, which is measured against *founded* settlements only - a site
   * history never settled is not a town and an inn beside it is correct.
   */
  private const val INN_SETTLEMENT_CLEARANCE = 500.0

  /**
   * The corrupted share of the land is the share the designer asked for.
   *
   * The claim `CorruptionStage`'s quantile solve makes, and the only thing that can falsify it. A fixed
   * threshold would fail this on most seeds, which is why there is not one; a solve that took its quantile
   * over the wrong denominator - every cell rather than the land - would fail it on every seed with an
   * unusual ocean share, which is the mistake worth catching here.
   *
   * Never vacuous: every world this pipeline produces has land, floored at 5% by `checkLandFraction`.
   */
  private fun checkCorruptionHitsItsTarget(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    if (generated.world.layers[LayerId.CORRUPTION] == null) return

    val target = generated.params.corruption.corruptedLandShare
    val actual = corruptedLandShare(generated)

    if (abs(actual - target) > CORRUPTED_SHARE_TOLERANCE) {
      fail(
        "corrupted land share is on target",
        "%.3f of the land is corrupted, asked for %.3f".format(actual, target)
      )
    }
  }

  /**
   * Standing settlements hold the corruption back.
   *
   * Falsifies "suppression is computed and then multiplied by something that is one everywhere", which is
   * exactly the shape a shipped-dead subsystem takes here: the corrupted share would still hit its target,
   * the map would still look plausible, and towns would sit in blight.
   *
   * Deliberately **not** "no corruption near a town". That would be false at the fringe of a province that
   * reaches a village, and forbidding it would rule out the corrupted-cellar-under-a-city hook that
   * `CorruptionParams.suppressionStrength` is deliberately below one to allow. What is asserted is that the
   * near-town mean is well under the land mean - a ratio, so it does not move when the target does.
   */
  private fun checkCorruptionAvoidsCivilisation(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val corruption = generated.world.layers[LayerId.CORRUPTION] as? FloatLayer ?: return
    val civDistance = generated.world.layers[LayerId.CIVILISATION_DISTANCE] as? FloatLayer ?: return
    val elevation = generated.world.layers[LayerId.ELEVATION] as? FloatLayer ?: return
    val seaLevel = generated.config.seaLevel
    val near = generated.params.corruption.suppressionRange / 3.0

    var nearSum = 0.0
    var nearCount = 0
    var landSum = 0.0
    var landCount = 0

    for (i in corruption.data.indices) {
      if (elevation.data[i] <= seaLevel) continue
      landSum += corruption.data[i]
      landCount++
      if (civDistance.data[i] <= near) {
        nearSum += corruption.data[i]
        nearCount++
      }
    }

    // A world history emptied entirely has no standing settlement and therefore no near-town cells. That is
    // a legitimate seed and there is nothing to assert about it - but say so out loud rather than returning
    // silently, because "the check skipped its subject" is how this module spent a year with zero lakes.
    if (nearCount == 0 || landCount == 0) return

    val nearMean = nearSum / nearCount
    val landMean = landSum / landCount
    if (landMean <= 0.0) return

    if (nearMean > landMean * CIVILISATION_SUPPRESSION_RATIO) {
      fail(
        "corruption avoids civilisation",
        "mean corruption within %.0f m of a town is %.3f against %.3f over all land"
          .format(near, nearMean, landMean)
      )
    }
  }


  /**
   * Every wound stands in corrupted ground, and the log says how it got there.
   *
   * ### What this actually guards, since the placement makes half of it true by construction
   *
   * `CorruptionStage.woundLift` pins the field to 1.0 at a wound's centre, so "the corruption there is high"
   * cannot fail as arithmetic. What can fail is the *join*: this stage reads `FeatureKind.WOUND` markers out of
   * the feature store, and a `HistoryStage` that stopped emitting them - or emitted them under another kind, or
   * after the query bounds moved - would leave the lift reading an empty list and look like nothing at all. Six
   * of fifteen wounds sat on clean grass before the lift existed and every test in the module was green.
   *
   * The second half is not construction at all: a `WOUND` on the ground with no [EventKind.STAR_FELL] naming it
   * is the lighthouse failure again - four structures in the world and nothing in the chronicle saying who put
   * them there - and that one is a real assertion about [HistorySim].
   *
   * The count goes in the census rather than being required here. A world whose mana peak is at sea, in a lake
   * or inside a town's clearance legitimately has no wound; a *zero* across a whole sweep is the failure, and
   * that is what a printed figure catches and a per-world check cannot.
   */
  private fun checkWoundsAreInCorruptedGround(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val corruption = generated.world.layers[LayerId.CORRUPTION] as? FloatLayer ?: return
    val chronicle = generated.world.chronicle
    val wounds = chronicle.sitesOfKind(SiteKind.WOUND)
    if (wounds.isEmpty()) return

    val fell = chronicle.events.count { it.kind == EventKind.STAR_FELL }
    if (fell == 0) {
      fail(
        "wounds are in corrupted ground",
        "${wounds.size} wounds on the map and no STAR_FELL in the chronicle to explain any of them"
      )
      return
    }

    for (site in wounds) {
      val at = corruption.sampleBilinear(site.position.x, site.position.y)
      if (at < CorruptionStage.CORRUPTED) {
        fail(
          "wounds are in corrupted ground",
          "the wound at (${site.position.x.toInt()}, ${site.position.y.toInt()}) stands in corruption " +
              "%.2f, below the %.2f that counts as corrupted - is CorruptionStage still reading WOUND markers?"
            .format(at, CorruptionStage.CORRUPTED)
        )
        return
      }
    }
  }

  /**
   * The world has dens on it at all.
   *
   * Safe to assert unconditionally, unlike the sea-lane existence check that could not be: every world this
   * pipeline produces has land, and land takes spawners at a floor acceptance rate. A zero here is the
   * shipped-dead failure and nothing else.
   */
  private fun checkTheWorldHasSpawners(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    // Nothing to say on a partial pipeline that never ran the stage; a world that ran it and produced none
    // is the failure.
    if (generated.world.features.all().none { it.kind == FeatureKind.BESTIA_SPAWN }) {
      val ran = generated.world.pipelineVersion != 0L &&
          generated.world.layers[LayerId.CORRUPTION] != null
      if (ran) fail("the world has spawners", "the spawner stage ran and produced no dens at all")
    }
  }

  /** No den in the sea, and none in a lake. Two different questions, one check - `checkNoSettlementInTheSea`'s shape. */
  private fun checkSpawnersAreOnDryLand(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val elevation = generated.world.layers[LayerId.ELEVATION] as? FloatLayer ?: return
    val waterLevel = generated.world.layers[LayerId.WATER_LEVEL] as? FloatLayer ?: return
    val config = generated.config
    val metres = config.baseResolution.metresPerCell
    val seaLevel = config.seaLevel

    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.BESTIA_SPAWN) continue
      val marker = feature as? PointMarker ?: continue

      val x = (marker.position.x / metres).toInt()
      val y = (marker.position.y / metres).toInt()

      if (elevation[x, y] <= seaLevel) {
        fail("spawners are on dry land", "a den at ${marker.position} is under the sea")
        return
      }
      if (!waterLevel[x, y].isNaN()) {
        fail("spawners are on dry land", "a den at ${marker.position} is under a lake")
        return
      }
    }
  }

  /** Every channel inside the range its consumer assumes. */
  private fun checkSpawnersAreWellFormed(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val maxLevel = generated.params.spawner.maxLevel

    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.BESTIA_SPAWN) continue
      val marker = feature as? PointMarker ?: continue

      val low = marker.attribute(SpawnerChannels.LEVEL_MIN).toInt()
      val high = marker.attribute(SpawnerChannels.LEVEL_MAX).toInt()
      val danger = marker.attribute(SpawnerChannels.DANGER)
      val pack = marker.attribute(SpawnerChannels.PACK).toInt()
      val radius = marker.attribute(SpawnerChannels.RADIUS)
      val biome = marker.attribute(SpawnerChannels.BIOME).toInt()

      val problem = when {
        low < 1 || high > maxLevel -> "levels $low..$high outside 1..$maxLevel"
        low > high -> "level range $low..$high is inverted"
        danger !in 0.0..1.0 -> "danger $danger is not a share"
        pack < 1 -> "pack $pack is empty"
        radius <= 0.0 -> "radius $radius is not positive"
        biome !in net.bestia.worldgen.bio.Biome.entries.indices -> "biome ordinal $biome is out of range"
        else -> null
      }

      if (problem != null) {
        fail("spawners are well formed", "a den at ${marker.position}: $problem")
        return
      }
    }
  }

  /**
   * The world has stands on it at all.
   *
   * `checkTheWorldHasSpawners`' shape, guarded on `CANOPY_COVER` rather than on the corruption because that
   * is the layer this stage cannot work without. A zero is the shipped-dead failure.
   */
  private fun checkTheWorldHasVegetationStands(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    if (generated.world.features.all().none { it.kind == FeatureKind.VEGETATION_STAND }) {
      val ran = generated.world.pipelineVersion != 0L &&
          generated.world.layers[LayerId.CANOPY_COVER] != null
      if (ran) fail("the world has vegetation stands", "the stand stage ran and produced none at all")
    }
  }

  /** No stand in the sea, and none in a lake. `checkSpawnersAreOnDryLand`'s two questions. */
  private fun checkVegetationStandsAreOnDryLand(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val elevation = generated.world.layers[LayerId.ELEVATION] as? FloatLayer ?: return
    val waterLevel = generated.world.layers[LayerId.WATER_LEVEL] as? FloatLayer ?: return
    val metres = generated.config.baseResolution.metresPerCell
    val seaLevel = generated.config.seaLevel

    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.VEGETATION_STAND) continue
      val marker = feature as? PointMarker ?: continue

      val x = (marker.position.x / metres).toInt()
      val y = (marker.position.y / metres).toInt()

      if (elevation[x, y] <= seaLevel) {
        fail("vegetation stands are on dry land", "a stand at ${marker.position} is under the sea")
        return
      }
      if (!waterLevel[x, y].isNaN()) {
        fail("vegetation stands are on dry land", "a stand at ${marker.position} is under a lake")
        return
      }
    }
  }

  /** Every channel inside the range its consumer assumes. */
  private fun checkVegetationStandsAreWellFormed(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.VEGETATION_STAND) continue
      val marker = feature as? PointMarker ?: continue

      val radius = marker.attribute(VegetationStandChannels.RADIUS)
      val cover = marker.attribute(VegetationStandChannels.COVER)
      val biome = marker.attribute(VegetationStandChannels.BIOME).toInt()
      val corruption = marker.attribute(VegetationStandChannels.CORRUPTION)
      val capacity = marker.attribute(VegetationStandChannels.CAPACITY)

      val problem = when {
        radius <= 0.0 -> "radius $radius is not positive"
        cover !in 0.0..1.0 -> "cover $cover is not a share"
        biome !in net.bestia.worldgen.bio.Biome.entries.indices -> "biome ordinal $biome is out of range"
        corruption !in 0.0..1.0 -> "corruption $corruption is not a share"
        capacity < 1.0 -> "capacity $capacity would give a runtime nothing to look after"
        else -> null
      }

      if (problem != null) {
        fail("vegetation stands are well formed", "a stand at ${marker.position}: $problem")
        return
      }
    }
  }

  /**
   * A stand sits on ground that is wooded, and more wooded than the world's land average.
   *
   * The comparative clause is the one worth having. `cover > 0` is satisfied by a stage that scatters
   * uniformly over any cell with a single tree in it, which is precisely the failure a stand-placement bug
   * would produce - the absolute test would pass and every stand would be looking after a hedge.
   * `checkCanopyCoverAgreesWithTheBiome` makes the same argument about the same layer.
   */
  private fun checkVegetationStandsAreWooded(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    // Present-or-skip, not bound: `meanOverLand` looks the layer up by id itself.
    if (generated.world.layers[LayerId.CANOPY_COVER] !is FloatLayer) return

    var sum = 0.0
    var count = 0

    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.VEGETATION_STAND) continue
      val marker = feature as? PointMarker ?: continue

      val cover = marker.attribute(VegetationStandChannels.COVER)
      if (cover <= 0.0) {
        fail("vegetation stands are wooded", "a stand at ${marker.position} has no canopy over it at all")
        return
      }

      sum += cover
      count++
    }

    if (count == 0) return

    val atStands = sum / count
    val overLand = meanOverLand(generated, LayerId.CANOPY_COVER)

    if (atStands < overLand * STAND_COVER_MARGIN) {
      fail(
        "vegetation stands are wooded",
        "stands average %.3f canopy against %.3f over all land - they are not finding the woods"
          .format(atStands, overLand)
      )
    }
  }

  /**
   * A stand advertises a capacity the chunk tier will actually fill.
   *
   * **The one that earns its cost.** `CAPACITY` is computed in the world tier from `VegetationParams` and the
   * props are emitted in the chunk tier from the same object, and nothing else would notice if the two drifted
   * - both numbers would stay perfectly plausible. The direct analogue of the tonnage clause in
   * `checkDepositsAreWellFormed`, one tier down.
   *
   * Strided over a handful of stands, because each one materialises the column heights of every chunk its disc
   * covers. Compared with a wide tolerance on purpose: the advertisement is an expectation over a Poisson-ish
   * process and one disc is one sample of it, so this is a check against being wrong by a factor, not against
   * being wrong by a fifth.
   */
  private fun checkVegetationStandsAdvertiseFillableCapacity(
    generated: GeneratedWorld,
    fail: (String, String) -> Unit
  ) {
    val stands = generated.world.features.all()
      .filter { it.kind == FeatureKind.VEGETATION_STAND }
      .filterIsInstance<PointMarker>()
    if (stands.isEmpty()) return

    val extent = generated.config.chunkSize * generated.config.voxelSize
    val stride = maxOf(1, stands.size / CAPACITY_SAMPLES)

    var advertised = 0.0
    var found = 0

    for (i in stands.indices step stride) {
      val stand = stands[i]
      val radius = stand.attribute(VegetationStandChannels.RADIUS)
      advertised += stand.attribute(VegetationStandChannels.CAPACITY)

      val fromChunkX = Math.floorDiv(((stand.position.x - radius) / extent).toLong(), 1L).toInt()
      val untilChunkX = Math.floorDiv(((stand.position.x + radius) / extent).toLong(), 1L).toInt()
      val fromChunkY = Math.floorDiv(((stand.position.y - radius) / extent).toLong(), 1L).toInt()
      val untilChunkY = Math.floorDiv(((stand.position.y + radius) / extent).toLong(), 1L).toInt()

      for (chunkY in fromChunkY..untilChunkY) {
        for (chunkX in fromChunkX..untilChunkX) {
          val props = generated.propsIn(chunkX, chunkY)
          for (p in props.indices) {
            if (props.kindAt(p) != PropKind.TREE) continue
            val dx = props.xAt(p) - stand.position.x
            val dy = props.yAt(p) - stand.position.y
            if (dx * dx + dy * dy <= radius * radius) found++
          }
        }
      }
    }

    if (advertised <= 0.0) return

    val ratio = found / advertised
    if (ratio < CAPACITY_MIN_RATIO || ratio > CAPACITY_MAX_RATIO) {
      fail(
        "vegetation stands advertise a fillable capacity",
        "stands advertise %.0f trees and the chunk tier emits %d, a ratio of %.2f - the two tiers disagree "
            .format(advertised, found, ratio) + "about the entity lattice"
      )
    }
  }

  /**
   * Every prop stands on its own ground, in its own chunk, under its own name.
   *
   * Three properties in one traversal because they share the expensive part. The `ground` equality is asserted
   * **exactly**, with no tolerance: the only way to be a little bit wrong here is to have sampled the base
   * heightfield instead of the stamped column heights, and that is a defect the tolerance would hide.
   * `WildSpawnerService.resolve` has exactly that bug shape for a den's z.
   */
  private fun checkPropsAreWellPlaced(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    if (generated.world.layers[LayerId.CANOPY_COVER] == null) return

    val config = generated.config
    val extent = config.chunkExtent
    val chunksAcross = (config.widthCells * config.baseResolution.metresPerCell / extent).toInt()
    if (chunksAcross <= 0) return

    val stride = maxOf(1, chunksAcross / PROP_PLACEMENT_SAMPLES)
    val names = HashSet<Long>()
    var checked = 0

    var chunkY = 0
    while (chunkY < chunksAcross) {
      var chunkX = 0
      while (chunkX < chunksAcross) {
        val heights = generated.columns.heights(ChunkPos(chunkX, chunkY), 0)
        val props = generated.materializer.propsIn(chunkX, chunkY, heights)

        for (i in props.indices) {
          if (!names.add(props.identityAt(i))) {
            fail(
              "props are well placed",
              "two props share the name ${props.identityAt(i)} - stored state would collide"
            )
            return
          }

          val columnX = Math.floorDiv(
            Math.floor(props.xAt(i) / config.voxelSize).toLong(), config.chunkSize.toLong()
          )
          val columnY = Math.floorDiv(
            Math.floor(props.yAt(i) / config.voxelSize).toLong(), config.chunkSize.toLong()
          )
          if (columnX.toInt() != chunkX || columnY.toInt() != chunkY) {
            fail(
              "props are well placed",
              "a prop at (${props.xAt(i)},${props.yAt(i)}) was emitted by chunk ($chunkX,$chunkY) but stands " +
                  "in ($columnX,$columnY)"
            )
            return
          }

          val voxelX = Math.floor(props.xAt(i) / config.voxelSize).toLong()
          val voxelY = Math.floor(props.yAt(i) / config.voxelSize).toLong()
          val localX = (voxelX - chunkX.toLong() * config.chunkSize).toInt()
          val localY = (voxelY - chunkY.toLong() * config.chunkSize).toInt()

          if (heights[localX, localY] != props.groundAt(i)) {
            fail(
              "props are well placed",
              "a prop at (${props.xAt(i)},${props.yAt(i)}) sits at ${props.groundAt(i)} where its column " +
                  "reads ${heights[localX, localY]} - somebody sampled the base heightfield"
            )
            return
          }

          checked++
        }

        chunkX += stride
      }
      chunkY += stride
    }

    if (checked == 0) {
      fail("props are well placed", "no prop was found anywhere in the sampled chunks, so nothing was checked")
    }
  }

  /**
   * Nothing above the cap inside the ring around a home village.
   *
   * The check that is invisible until a level-one master walks out of the gate and is eaten. Skips cleanly
   * on a world history emptied of home candidates, which is legitimate and rare.
   */
  private fun checkSpawnersNearHomeAreGentle(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val homes = SettlementSpawnPoints.choose(generated)
    if (homes.isEmpty()) return

    val params = generated.params.spawner
    val wrap = WorldWrap(generated.config)

    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.BESTIA_SPAWN) continue
      val marker = feature as? PointMarker ?: continue

      val near = homes.any {
        wrap.distance(marker.position.x, marker.position.y, it.position.x, it.position.y) <
            params.homeSafeRadius
      }
      if (!near) continue

      val high = marker.attribute(SpawnerChannels.LEVEL_MAX).toInt()
      if (high > params.homeMaxLevel) {
        fail(
          "spawners near home are gentle",
          "a den at ${marker.position} reaches level $high inside the home ring"
        )
        return
      }
    }
  }

  /**
   * Corrupted land is endgame and settled country is not.
   *
   * **Two-sided deliberately.** A one-sided "corrupted land is level eighty" passes against a danger field
   * that is a constant eighty everywhere, which is a world with no ramp in it at all - and that is the
   * likelier bug, because a broken weighted sum saturates rather than zeroing.
   *
   * ### The settled half is a ratio, and it was an absolute
   *
   * It read `mean level near civilisation <= 35`, and that number is only meaningful on a world with a normal
   * amount of civilisation on it. A 200-seed sweep found seed 184: eleven settlements over a 192 km world,
   * most of them in hard country, near-town mean level 42.7. Nothing was wrong with the spawn ramp - the world
   * simply has very little settled country, and what it has is in the mountains. The same seed fails the
   * absolute with the blight switched off entirely (39.9), so this was never about the mana.
   *
   * The claim worth making is *the country around people is gentler than the wilderness*, which is a contrast
   * and does not depend on how much of either there is. Measured over twenty seeds the ratio runs 0.41 to 0.71
   * with a median of 0.51, and seed 184 sits at 0.82 - still the outlier, and now legibly so rather than as a
   * pass/fail cliff. `checkCorruptionAvoidsCivilisation` made this exact move for this exact reason, and its
   * KDoc says so: "a ratio, so it does not move when the target does".
   *
   * What that trades away, stated plainly: a ramp uniformly *half* as steep would keep its ratio and pass here.
   * The counter is `spawnerCensus`, whose four band counts the sweep prints per seed - a flattened ramp empties
   * the 1-8 band, and a zero there is visible on every line.
   */
  private fun checkSpawnersRespectCorruption(generated: GeneratedWorld, fail: (String, String) -> Unit) {
    val params = generated.params.spawner

    var corruptedSum = 0.0
    var corruptedCount = 0
    var settledSum = 0.0
    var settledCount = 0
    var worldSum = 0.0
    var worldCount = 0

    val civDistance = generated.world.layers[LayerId.CIVILISATION_DISTANCE] as? FloatLayer ?: return
    val metres = generated.config.baseResolution.metresPerCell

    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.BESTIA_SPAWN) continue
      val marker = feature as? PointMarker ?: continue

      val level = marker.attribute(SpawnerChannels.LEVEL_MIN).toDouble()
      worldSum += level
      worldCount++

      if (marker.attribute(SpawnerChannels.CORRUPTION) >= CorruptionStage.CORRUPTED) {
        corruptedSum += level
        corruptedCount++
      }

      val x = (marker.position.x / metres).toInt()
      val y = (marker.position.y / metres).toInt()
      if (civDistance[x, y] <= SETTLED_RANGE_METRES) {
        settledSum += level
        settledCount++
      }
    }

    if (corruptedCount > 0) {
      val mean = corruptedSum / corruptedCount
      if (mean < params.corruptedMinLevel - params.levelSpread) {
        fail(
          "spawners respect corruption",
          "mean level in corrupted land is %.1f, below the band floor".format(mean)
        )
      }
    }

    // A world history emptied entirely has no settled country to compare, which is a legitimate seed and not
    // something to assert about.
    if (settledCount > 0 && worldCount > 0) {
      val settledMean = settledSum / settledCount
      val worldMean = worldSum / worldCount
      if (worldMean > 0.0 && settledMean > worldMean * SETTLED_MAX_LEVEL_RATIO) {
        fail(
          "spawners respect corruption",
          ("mean level within %d m of a road or town is %.1f against %.1f over the whole world " +
              "(%.2f of it, limit %.2f)").format(
            SETTLED_RANGE_METRES.toInt(), settledMean, worldMean,
            settledMean / worldMean, SETTLED_MAX_LEVEL_RATIO
          )
        )
      }
    }
  }

}
