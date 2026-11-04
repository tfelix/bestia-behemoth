package net.bestia.worldgen.civ

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.worldgen.resource.DepositChannels
import net.bestia.worldgen.resource.ResourceType
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.FootprintFeature
import net.bestia.worldgen.vector.Intersections
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CivilisationStageTest {

  private companion object {
    /** Metres from a river's line within which a road need not be on a street: it is fording or bridging. */
    const val RIVER_EXCUSE = 60.0

    /**
     * Big enough for a proper trade network: several cities, towns between them, and enough road mileage that
     * some of it has to cross a river.
     *
     * The size is load bearing. Crossing a river costs eight times ordinary ground, so routes go round one
     * where they can; it takes a few dozen roads before any of them is forced across, and below that the bridge
     * tests pass vacuously while testing nothing.
     */
    val world by lazy {
      TestWorlds.get(
        WorldConfig(seed = 0x50FA5L, widthCells = 288, heightCells = 288, chunkSize = 32, voxelSize = 1.0)
      )
    }
  }

  // --- Resources -------------------------------------------------------------------------------------

  @Test
  fun `deposits are placed and carry everything a mine would need to know`() {
    val deposits = world.world.features.all()
      .filter { it.kind == FeatureKind.ORE_DEPOSIT }
      .filterIsInstance<PointMarker>()

    assertTrue(deposits.isNotEmpty(), "the world has no deposits at all")

    for (deposit in deposits) {
      val type = deposit.attribute(DepositChannels.TYPE).toInt()
      assertTrue(type in ResourceType.entries.indices, "resource type $type is not a type")
      assertTrue(deposit.attribute(DepositChannels.RICHNESS) in 0.0..1.0)
      assertTrue(deposit.attribute(DepositChannels.TONS) > 0.0)
      assertTrue(deposit.attribute(DepositChannels.RADIUS) > 0.0)
      assertTrue(deposit.attribute(DepositChannels.DEPTH) >= 0.0)
    }
  }

  @Test
  fun `several kinds of resource are present rather than one`() {
    // A suitability field that has collapsed - every rule returning zero, or one rule returning one - shows up
    // here and nowhere else, because the deposit count alone would look fine.
    val kinds = world.world.features.all()
      .filter { it.kind == FeatureKind.ORE_DEPOSIT }
      .filterIsInstance<PointMarker>()
      .map { ResourceType.entries[it.attribute(DepositChannels.TYPE).toInt()] }
      .toSet()

    assertTrue(kinds.size >= 4, "only $kinds were placed")
  }

  @Test
  fun `placer gold sits downstream of a lode and never upstream`() {
    // The mechanic this stage exists to enable: a player who pans gold out of a river gravel can walk upstream
    // and find the lode, because the world put it there for that reason.
    val deposits = world.world.features.all()
      .filter { it.kind == FeatureKind.ORE_DEPOSIT }
      .filterIsInstance<PointMarker>()

    val lodes = deposits.filter {
      it.attribute(DepositChannels.TYPE).toInt() == ResourceType.GOLD_LODE.ordinal
    }
    val placers = deposits.filter {
      it.attribute(DepositChannels.TYPE).toInt() == ResourceType.GOLD_PLACER.ordinal
    }

    if (lodes.isEmpty()) return
    if (placers.isEmpty()) return

    val elevation = world.world.layers.require<FloatLayer>(LayerId.ELEVATION)
    val metres = elevation.region.resolution.metresPerCell

    fun elevationAt(point: Vec2d) =
      elevation[(point.x / metres).toInt(), (point.y / metres).toInt()].toDouble()

    for (placer in placers) {
      // Every placer was traced from some lode, so at least one lode must be at or above it. Testing "some
      // lode" rather than "its own lode" keeps the assertion honest about what the data records.
      val hasSourceAbove = lodes.any { elevationAt(it.position) >= elevationAt(placer.position) - 1.0 }
      assertTrue(hasSourceAbove, "a placer at ${placer.position} has no lode at or above it")
    }
  }

  // --- Habitability ----------------------------------------------------------------------------------

  @Test
  fun `habitability is zero on water and positive on good land`() {
    val habitability = world.world.layers.require<FloatLayer>(LayerId.HABITABILITY)
    val water = world.world.layers.require<FloatLayer>(LayerId.WATER_LEVEL)
    val region = habitability.region

    var bestLand = 0.0
    for (y in region.minY..region.maxY) {
      for (x in region.minX..region.maxX) {
        if (!water[x, y].isNaN()) {
          assertEquals(0f, habitability[x, y], "water at ($x,$y) is habitable")
        } else {
          bestLand = maxOf(bestLand, habitability[x, y].toDouble())
        }
      }
    }

    assertTrue(bestLand > 0.4, "the best land in the world only scored $bestLand")
  }

  @Test
  fun `different cultures prefer different places`() {
    // The cheapest source of civilisational variety there is, so it is worth checking it actually varies. If
    // every culture scored the same cell best, the weights would be doing nothing.
    val terms = ProbeTerms.of(world)

    val favourites = Culture.ALL.associateWith { culture ->
      (0 until terms.cellCount).maxByOrNull { terms.score(it, culture) } ?: 0
    }

    assertTrue(
      favourites.values.toSet().size >= 2,
      "every culture picked the same cell: ${favourites.values.first()}"
    )
  }

  @Test
  fun `movement cost is highest on water and lowest on flat open ground`() {
    val cost = world.world.layers.require<FloatLayer>(LayerId.MOVEMENT_COST)
    val water = world.world.layers.require<FloatLayer>(LayerId.WATER_LEVEL)
    val region = cost.region

    var landMinimum = Double.MAX_VALUE
    var sawWater = false

    for (y in region.minY..region.maxY) {
      for (x in region.minX..region.maxX) {
        if (!water[x, y].isNaN()) {
          sawWater = true
          assertTrue(cost[x, y] >= 100f, "water at ($x,$y) costs only ${cost[x, y]} to cross")
        } else {
          landMinimum = minOf(landMinimum, cost[x, y].toDouble())
        }
      }
    }

    assertTrue(sawWater, "the test world has no water")
    assertTrue(landMinimum in 0.9..2.0, "the easiest land in the world costs $landMinimum")
  }

  // --- Settlements -----------------------------------------------------------------------------------

  @Test
  fun `settlements are placed at every tier and carry their attributes`() {
    val sites = world.world.features.all()
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<PointMarker>()

    assertTrue(sites.isNotEmpty(), "no settlements were placed")

    val tiers = sites.map { SettlementTier.entries[it.attribute(SettlementChannels.TIER).toInt()] }.toSet()
    assertTrue(SettlementTier.CITY in tiers, "no cities: $tiers")

    for (site in sites) {
      val tier = SettlementTier.entries[site.attribute(SettlementChannels.TIER).toInt()]
      val population = site.attribute(SettlementChannels.POPULATION).toInt()
      assertTrue(
        population in tier.minPopulation..tier.maxPopulation,
        "a $tier has population $population, outside ${tier.minPopulation}..${tier.maxPopulation}"
      )
      assertTrue(site.attribute(SettlementChannels.CULTURE).toInt() in Culture.ALL.indices)
    }
  }

  @Test
  fun `every settlement has a grading feature and it never fills a river`() {
    // Grading is stamped after the river, so a generous fill limit would let a riverside town raise the channel
    // running through it to street level and dam it. The terrace profile may cut freely and fill barely.
    val sites = world.world.features.all().filter { it.kind == FeatureKind.SETTLEMENT }
    val gradings = world.world.features.all().filter { it.kind == FeatureKind.SETTLEMENT_GRADING }

    assertEquals(sites.size, gradings.size, "every settlement needs its ground levelling")

    val params = SettlementParams()
    val scratch = DoubleArray(0)
    for (grading in gradings) {
      // A channel eight metres below the pad must come back barely raised.
      var raised = Double.NaN
      grading.evaluateColumn(
        grading.bbox.centerX, grading.bbox.centerY, base = -8.0, scratch = scratch,
        sink = { _, _, _, value, _ -> raised = value }
      )
      if (!raised.isNaN()) {
        assertTrue(
          raised <= -8.0 + params.maxFill + 1e-9,
          "grading raised a channel from -8 to $raised, more than the ${params.maxFill} m fill limit"
        )
      }
    }
  }

  @Test
  fun `roads connect settlements and never run across the sea`() {
    val roads = world.world.features.all()
      .filter { it.kind == FeatureKind.ROAD }
      .filterIsInstance<PolylineFeature>()

    assertTrue(roads.isNotEmpty(), "no roads were built")

    val water = world.world.layers.require<FloatLayer>(LayerId.WATER_LEVEL)
    val metres = water.region.resolution.metresPerCell

    for (road in roads) {
      for (point in road.centerline.points) {
        val x = (point.x / metres).toInt()
        val y = (point.y / metres).toInt()
        if (!water.region.contains(x, y)) continue
        assertTrue(
          water[x, y].isNaN(),
          "a road passes through water at $point; two landmasses would need a sea lane, not a road"
        )
      }
    }
  }

  @Test
  fun `a road leaves a gap where it crosses a river`() {
    // Roads are stamped with REPLACE above the river's priority, so without a gap the carriageway overwrites
    // the channel and dams the river at every crossing. The gap makes it a ford; the bridge marker says where
    // a deck belongs.
    val roads = world.world.features.all()
      .filter { it.kind == FeatureKind.ROAD }
      .filterIsInstance<PolylineFeature>()
    val rivers = world.world.features.all()
      .filter { it.kind == FeatureKind.RIVER_CHANNEL }
      .filterIsInstance<PolylineFeature>()

    var checked = 0

    for (road in roads) {
      val roadCorridor = road.stations.channel(PolylineFeature.CORRIDOR_CHANNEL)
      for (river in rivers) {
        for (crossing in Intersections.of(road.centerline, river.centerline)) {
          val u = road.centerline.stationParamAt(crossing.sA)
          // Non-positive rather than exactly zero. Station values are interpolated with a cubic spline, so a
          // stretch of zeroes between two positive widths undershoots slightly below zero on the way in - which
          // is harmless and in fact safer than zero, because what matters is that the corridor never *exceeds*
          // zero. A feature whose corridor is not positive is skipped for every column, which is the property
          // this test is actually about.
          assertTrue(
            road.stations.sample(roadCorridor, u) <= 0.0,
            "the road still has a corridor where it crosses a river at ${crossing.point}: " +
                "${road.stations.sample(roadCorridor, u)}"
          )
          checked++
        }
      }
    }

    assertTrue(checked > 0, "no road crossed a river in this world, so nothing was tested")
  }

  @Test
  fun `a bridge marker is emitted for every crossing and carries its geometry`() {
    val bridges = world.world.features.all()
      .filter { it.kind == FeatureKind.BRIDGE }
      .filterIsInstance<PointMarker>()

    assertTrue(bridges.isNotEmpty(), "no bridges were emitted")

    for (bridge in bridges) {
      assertTrue(bridge.attribute(BridgeChannels.SPAN) > 0.0)
      assertTrue(bridge.attribute(BridgeChannels.HALF_WIDTH) > 0.0)

      // The bearing must be a unit vector, because the deck is laid out along it.
      val bx = bridge.attribute(BridgeChannels.BEARING_X)
      val by = bridge.attribute(BridgeChannels.BEARING_Y)
      assertEquals(1.0, kotlin.math.sqrt(bx * bx + by * by), 1e-6, "bearing is not a unit vector")
    }
  }

  // --- Road classes, and roads into towns ----------------------------------------------------------

  private val roads by lazy {
    world.world.features.all().filter { it.kind == FeatureKind.ROAD }.filterIsInstance<PolylineFeature>()
  }

  private val settlements by lazy {
    world.world.features.all().filter { it.kind == FeatureKind.SETTLEMENT }.filterIsInstance<PointMarker>()
  }

  private fun tierAt(at: Vec2d): SettlementTier? = settlements
    .firstOrNull { it.position.distanceTo(at) < 1e-6 }
    ?.let { SettlementTier.entries[it.attribute(SettlementChannels.TIER).toInt()] }

  private val small = setOf(SettlementTier.VILLAGE, SettlementTier.HAMLET)

  @Test
  fun `every road carries its class, and the classes are where they belong`() {
    assertTrue(roads.isNotEmpty(), "no roads were built")

    for (road in roads) {
      val roadClass = assertNotNull(RoadClass.of(road), "a road carries no class")
      val from = tierAt(road.centerline.points.first())
      val to = tierAt(road.centerline.points.last())

      // A village's way out is a trail; a way between two towns never is.
      if (from in small) assertEquals(RoadClass.TRAIL, roadClass, "a spur from a $from is a $roadClass")
      if (from != null && to != null && from !in small && to !in small) {
        assertTrue(roadClass != RoadClass.TRAIL, "a way between a $from and a $to is only a trail")
      }
    }

    // The point of the classes: the busiest ways out of the cities are paved.
    assertTrue(
      roads.any { RoadClass.of(it) == RoadClass.COBBLED &&
          (tierAt(it.centerline.points.first()) == SettlementTier.CITY ||
              tierAt(it.centerline.points.last()) == SettlementTier.CITY) },
      "no cobbled road leaves a city"
    )
  }

  @Test
  fun `a trunk ends exactly at the settlements it joins and a spur exactly on its trunk`() {
    // Both ends are where something else joins on - a town's high street, a spur's junction - so the meander
    // must have faded to nothing there.
    var spurs = 0
    for (road in roads) {
      val from = tierAt(road.centerline.points.first())
      assertNotNull(from, "a road starts somewhere that is not a settlement: ${road.centerline.points.first()}")

      if (from in small) {
        val end = road.centerline.points.last()
        val gap = roads.filter { it !== road }.minOf { it.centerline.project(end).distance }
        assertTrue(gap < 1.0, "a spur ends ${"%.1f".format(gap)} m short of the road it joins, at $end")
        spurs++
      } else {
        assertNotNull(tierAt(road.centerline.points.last()), "a trunk ends somewhere that is not a settlement")
      }
    }
    assertTrue(spurs > 0, "no village spurs were built, so nothing was tested")
  }

  @Test
  fun `roads meander rather than running ruled straight`() {
    // The routed line is kilometre cells with their corners cut, which leaves long runs at exactly 0, 45 and 90
    // degrees: interior vertices with no curvature at all. Meandered, almost none of them are straight.
    for (road in roads) {
      if (RoadClass.of(road) == RoadClass.COBBLED || road.centerline.length < 2_000.0) continue
      val line = road.centerline
      val straight = (1 until line.vertexCount - 1).count { abs(line.signedCurvatureAt(it)) < 1e-9 }
      assertTrue(
        straight < (line.vertexCount - 2) * 0.1,
        "$straight of ${line.vertexCount - 2} vertices of a ${RoadClass.of(road)} run dead straight"
      )
    }
  }

  @Test
  fun `no building stands on a road`() {
    val buildings = world.world.features.all().filter { it.kind == FeatureKind.BUILDING }.filterIsInstance<FootprintFeature>()
    assertTrue(buildings.isNotEmpty(), "no buildings were placed, so nothing was tested")

    for (building in buildings) {
      for (road in roads) {
        if (!road.bbox.contains(building.center.x, building.center.y)) continue
        for (corner in building.corners() + building.center) {
          val distance = road.centerline.project(corner).distance
          assertTrue(distance >= 4.0, "a building stands ${"%.1f".format(distance)} m from a road's line at $corner")
        }
      }
    }
  }

  @Test
  fun `a road into a town carries on as its high street`() {
    // Through the core of every town and city that was laid out, the road is a rank-0 street - the widest - and
    // not a line the town's own streets wander away from. River crossings are excused: a street stops at the
    // bank and the road fords or bridges it.
    val streets = world.world.features.all().filter { it.kind == FeatureKind.STREET }.filterIsInstance<PolylineFeature>()
    val rivers = world.world.features.all().filter { it.kind == FeatureKind.RIVER_CHANNEL }.filterIsInstance<PolylineFeature>()
    val highStreetHalfWidth = TownParams().streets.halfWidthOfRank(0)

    var sampled = 0
    var onAHighStreet = 0
    for (site in settlements) {
      val tier = SettlementTier.entries[site.attribute(SettlementChannels.TIER).toInt()]
      if (tier in small) continue
      val reach = tier.footprintRadius * 0.4
      val near = streets.filter { it.bbox.expanded(reach).contains(site.position.x, site.position.y) }
      // A settlement history abandoned has no streets at all, and nothing to continue the road.
      if (near.isEmpty()) continue

      for (road in roads) {
        var s = 0.0
        while (s < road.centerline.length) {
          val at = road.centerline.pointAt(s)
          s += 20.0
          if (at.distanceTo(site.position) > reach) continue
          if (rivers.any { it.bbox.contains(at.x, at.y) && it.centerline.project(at).distance < RIVER_EXCUSE }) continue

          sampled++
          val onStreet = near.any { street ->
            val hit = street.centerline.project(at)
            hit.distance < 3.0 &&
                street.stations.sample(street.stations.channel(Profiles.CHANNEL_HALF_WIDTH), hit.u) >=
                highStreetHalfWidth - 0.5
          }
          if (onStreet) onAHighStreet++
        }
      }
    }

    assertTrue(sampled > 100, "only $sampled road samples fell in a town core, so nothing much was tested")
    assertTrue(
      onAHighStreet >= sampled * 0.9,
      "only $onAHighStreet of $sampled road samples in town cores lie on a high street"
    )
  }

  // --- Route finding -------------------------------------------------------------------------------

  @Test
  fun `the route finder takes the cheap way round rather than the short way through`() {
    // A wall of expensive terrain with a gap in it. A straight line is shorter; the gap is cheaper. Roads
    // looking like roads depends entirely on this being true.
    val cost = Grid(21, 21, 1.0)
    for (y in 0 until 21) {
      if (y == 18) continue
      cost[10, y] = 60.0
    }

    val finder = RouteFinder(cost, metresPerCell = 100.0)
    val route = finder.route(start = 10 * 21 + 2, goal = 10 * 21 + 18)

    assertNotNull(route)
    val wentThroughGap = route.cells.any { it / 21 == 18 && it % 21 == 10 }
    assertTrue(wentThroughGap, "the route ignored the gap and pushed through the barrier")
  }

  @Test
  fun `the route finder gives up rather than searching forever`() {
    val cost = Grid(30, 30, 1.0)
    val finder = RouteFinder(cost, metresPerCell = 100.0, expansionLimit = 5)

    // One impossible or expensive route must not stall world generation.
    assertNull(finder.route(0, 29 * 30 + 29))
  }

  @Test
  fun `a route costs the same in both directions`() {
    // The trade network is undirected, so an asymmetric cost field would give two different roads between the
    // same pair of towns depending on which end was searched from.
    val cost = Grid(15, 15) { x, y -> 1.0 + x * 0.3 + y * 0.11 }
    val finder = RouteFinder(cost, metresPerCell = 100.0)

    val there = finder.route(0, 14 * 15 + 14)
    val back = finder.route(14 * 15 + 14, 0)

    assertNotNull(there)
    assertNotNull(back)
    assertEquals(there.cost, back.cost, 1e-6)
  }

  // --- Intersections -------------------------------------------------------------------------------

  @Test
  fun `crossing polylines are detected with the arc length along each`() {
    val a = Polyline(listOf(Vec2d(0.0, 0.0), Vec2d(100.0, 0.0)))
    val b = Polyline(listOf(Vec2d(40.0, -50.0), Vec2d(40.0, 50.0)))

    val crossings = Intersections.of(a, b)

    assertEquals(1, crossings.size)
    assertEquals(40.0, crossings[0].point.x, 1e-9)
    assertEquals(0.0, crossings[0].point.y, 1e-9)
    assertEquals(40.0, crossings[0].sA, 1e-9)
    assertEquals(50.0, crossings[0].sB, 1e-9)
  }

  @Test
  fun `lines that do not meet and lines that run alongside are not crossings`() {
    val road = Polyline(listOf(Vec2d(0.0, 0.0), Vec2d(100.0, 0.0)))

    assertTrue(Intersections.of(road, Polyline(listOf(Vec2d(0.0, 10.0), Vec2d(100.0, 10.0)))).isEmpty())
    // A road running *along* a river for a kilometre is not a crossing, and calling it one would put a bridge
    // in the middle of it.
    assertTrue(Intersections.of(road, Polyline(listOf(Vec2d(20.0, 0.0), Vec2d(80.0, 0.0)))).isEmpty())
  }

  /** Recomputes the habitability terms so a test can score them per culture. */
  private class ProbeTerms(private val terms: Terms, val cellCount: Int) {
    fun score(cell: Int, culture: Culture) = terms.scoreAt(cell, culture)

    companion object {
      fun of(world: net.bestia.worldgen.pipeline.GeneratedWorld): ProbeTerms {
        val region = world.world.layers.require<FloatLayer>(LayerId.ELEVATION).region
        val pipeline = StandardWorld.pipeline(world.config)
        val stage = pipeline.stage(HabitabilityStage.ID)
        val ctx = pipeline.contextFor(stage, world.world)
        return ProbeTerms(Terms.read(ctx, region, HabitabilityParams()), region.width * region.height)
      }
    }
  }

  @Test
  fun `every settlement records why it is where it is`() {
    val settlements = world.world.features.all()
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<PointMarker>()
    assertTrue(settlements.isNotEmpty(), "no settlements to ask")

    val causes = settlements.map {
      val ordinal = it.attribute(SettlementChannels.FOUNDING_CAUSE).toInt()
      assertTrue(
        ordinal in FoundingCause.entries.indices,
        "settlement carries founding cause $ordinal, which is not one"
      )
      FoundingCause.entries[ordinal]
    }

    // Habit six, and the failure it guards is specific: an ordinal channel that is never written reads back as
    // zero everywhere, and zero is a legitimate value here - so "every settlement has a valid cause" passes
    // just as happily on a channel nobody filled in.
    assertTrue(
      causes.any { it != FoundingCause.LAND },
      "every settlement in the world was founded on good land and nothing else, which means the four " +
          "geographic bonuses either never fired or never reached the marker"
    )
    assertTrue(
      causes.toSet().size >= 3,
      "only ${causes.toSet()} across ${causes.size} settlements"
    )
  }
}
