package net.bestia.worldgen.civ

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldWrap
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.PointMarker
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sea lanes: the water half of the trade network, and what closes deviation 7.
 *
 * The rejection that produced them was one line - a road route that touches water is thrown away - and the
 * information it discarded was exactly the pair a lane should join. Two things are worth testing about that.
 * The lanes have to be *over water* and clear of the wrap seam, which is correctness; and they have to actually
 * *exist* on a world that needs them, which is the property this module has historically got wrong. `hydro/Lakes`
 * was complete and unit-tested and received no basin for a year, and the first version of this pass produced no
 * lane on any of forty worlds because it looked three kilometres for a harbour.
 *
 * Seed 9 at 192 cells is pinned because it has two lanes. The demo world at 512 has none - all forty-two of its
 * cities sit on one landmass - which is a fact about that seed rather than about the code, and is why a test
 * that only looked at the reference world would assert nothing at all.
 */
class SeaLaneTest {

  private companion object {
    val world: GeneratedWorld = StandardWorld.build(
      StandardWorld.demoConfig(seed = 9L).copy(widthCells = 192, heightCells = 192)
    )

    val lanes: List<MarkerFeature> = world.world.features.all()
      .filterIsInstance<MarkerFeature>()
      .filter { it.kind == FeatureKind.SEA_LANE }

    val settlements: List<PointMarker> = world.world.features.all()
      .filterIsInstance<PointMarker>()
      .filter { it.kind == FeatureKind.SETTLEMENT }
  }

  private fun isOcean(worldX: Double, worldY: Double): Boolean {
    val waterLevel: FloatLayer = world.world.layers.require(LayerId.WATER_LEVEL)
    val lakeId: IntLayer = world.world.layers.require(LayerId.LAKE_ID)
    val metres = waterLevel.region.resolution.metresPerCell
    val x = (worldX / metres).toInt()
    val y = (worldY / metres).toInt()

    return !waterLevel[x, y].isNaN() && lakeId[x, y] == 0
  }

  @Test
  fun `the pinned world has sea lanes at all`() {
    // Stated unconditionally and first, for the reason `checkTheWorldHasStandingWater` is: every property
    // below is a claim about lanes, and a claim about lanes passes vacuously when there are none.
    assertTrue(lanes.isNotEmpty(), "seed 9 at 192 cells produced no sea lane, so nothing below asserts anything")
  }

  @Test
  fun `every station on a lane is over open water`() {
    // The definition of the thing. The water cost field makes land finite-but-huge rather than forbidden, on
    // the same argument the movement cost field does, so a route that crawls overland is expressible and has to
    // be excluded rather than assumed away.
    for (lane in lanes) {
      for (point in lane.centerline.points) {
        assertTrue(
          isOcean(point.x, point.y),
          "a station of ${lane.id} at (${point.x.toInt()}, ${point.y.toInt()}) is not over ocean"
        )
      }
    }
  }

  @Test
  fun `no lane enters the ocean margin`() {
    // The margin is the 2.5 km of forced deep water that hides the wrap seam, so a lane through it is a road
    // across the seam by another name: follow it and you sail off one edge of the world onto the other. The
    // water cost field forbids those cells outright, which makes this hold by construction - and asserting it
    // anyway is what would catch the cost field being built from the wrong predicate.
    val wrap = WorldWrap(world.config)

    for (lane in lanes) {
      for (point in lane.centerline.points) {
        assertTrue(
          !wrap.isInOceanBorder(point.x, point.y),
          "${lane.id} passes through the ocean margin at (${point.x.toInt()}, ${point.y.toInt()})"
        )
      }
    }
  }

  @Test
  fun `a lane names two real settlements, and not the same one twice`() {
    val indices = settlements.map { it.attribute(SettlementChannels.INDEX).toInt() }.toSet()
    assertTrue(indices.isNotEmpty(), "the world has no settlement indices to join against")

    for (lane in lanes) {
      val from = lane.attributeAt(lane.channel(SeaLaneChannels.FROM_SETTLEMENT), 0.0, 0.0).toInt()
      val to = lane.attributeAt(lane.channel(SeaLaneChannels.TO_SETTLEMENT), 0.0, 0.0).toInt()

      assertTrue(from in indices, "${lane.id} names settlement $from, which does not exist")
      assertTrue(to in indices, "${lane.id} names settlement $to, which does not exist")
      assertTrue(from != to, "${lane.id} connects settlement $from to itself")
      // Lower index first, so a consumer can key on the pair without normalising it.
      assertTrue(from < to, "${lane.id} reports its endpoints as ($from, $to), which is not in order")
    }
  }

  @Test
  fun `the endpoint indices are the same from either end of the lane`() {
    // The channels are constant along the lane, which is what makes "which two places does this connect"
    // answerable from an arbitrary arc length - the only access a station table gives a consumer.
    for (lane in lanes) {
      val fromChannel = lane.channel(SeaLaneChannels.FROM_SETTLEMENT)
      val head = lane.centerline.points.first()
      val tail = lane.centerline.points.last()

      assertEquals(
        lane.attributeAt(fromChannel, head.x, head.y),
        lane.attributeAt(fromChannel, tail.x, tail.y),
        1e-9,
        "${lane.id} reports different endpoints depending on where it is sampled"
      )
    }
  }

  @Test
  fun `depth is positive along a lane`() {
    for (lane in lanes) {
      val depth = lane.channel(SeaLaneChannels.DEPTH)
      for (point in lane.centerline.points) {
        val here = lane.attributeAt(depth, point.x, point.y)
        assertTrue(here > 0.0, "${lane.id} records a depth of $here at (${point.x.toInt()}, ${point.y.toInt()})")
      }
    }
  }

  @Test
  fun `a lane carries no terrain effect`() {
    // A MarkerFeature rather than a PolylineFeature, and the reason is not only that a ship leaves no mark. A
    // lane's bounding box spans an ocean, so it lands in FeatureIndex's oversized list and is handed to every
    // chunk query in the world; `affectsHeight` being false is what makes FeatureEvaluator drop it immediately
    // rather than evaluating a corridor across half the map.
    for (lane in lanes) {
      assertTrue(!lane.affectsHeight, "${lane.id} claims to affect height")
      assertEquals(0.0, lane.corridorWidthMax, 0.0, "${lane.id} has a corridor to stamp")
    }
  }

  @Test
  fun `a lane replaces a road rather than duplicating one`() {
    // The two edge types are one graph: a pair reaches the lane builder only after its land route was rejected,
    // so no pair may have both. If this fails, `simulateTraffic` is being handed two routes under one key and
    // one of them is silently lost.
    val roadPairs = HashSet<Pair<Int, Int>>()
    val lanePairs = HashSet<Pair<Int, Int>>()

    for (lane in lanes) {
      val from = lane.attributeAt(lane.channel(SeaLaneChannels.FROM_SETTLEMENT), 0.0, 0.0).toInt()
      val to = lane.attributeAt(lane.channel(SeaLaneChannels.TO_SETTLEMENT), 0.0, 0.0).toInt()
      lanePairs.add(from to to)
    }

    assertTrue(lanePairs.size == lanes.size, "two lanes join the same pair of settlements")
    assertTrue(roadPairs.intersect(lanePairs).isEmpty())
  }

  @Test
  fun `lanes appear on a reasonable share of worlds and not on all of them`() {
    // The honest framing of how often this fires. Every rejected pair is two cities either side of a bay, so a
    // world whose cities all sit on one landmass has none - and the 512 km demo world is one of those. A rate
    // near zero would mean the pass is dead again; a rate of one would mean the road rejection is firing when
    // it should not.
    var withLanes = 0
    val worlds = 15

    for (seed in 1L..worlds) {
      val built = StandardWorld.build(
        StandardWorld.demoConfig(seed).copy(widthCells = 192, heightCells = 192)
      )
      if (built.world.features.all().any { it.kind == FeatureKind.SEA_LANE }) withLanes++
    }

    val share = withLanes.toDouble() / worlds
    assertTrue(
      withLanes in 1 until worlds,
      "${"%.0f".format(Locale.ROOT, 100 * share)}% of worlds have sea lanes, which is either none or all"
    )
  }
}
