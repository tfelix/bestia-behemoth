package net.bestia.worldgen.civ

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The village layout: a settlement built on the way through it rather than grown from its middle.
 *
 * The properties worth pinning are the two that decide whether the model may be used at all - it must refuse a
 * settlement it cannot house, and it must keep every street inside the ground the settlement graded - plus the
 * one that makes it a village rather than a town: the high street is a way that already existed, not a new
 * curve laid beside it.
 */
class RoadsideVillageTest {

  private val centre = Vec2d(10_000.0, 10_000.0)
  private val params = TownParams()

  /** The bound `TownStage` passes: a village's, so the numbers here are the ones a village actually gets. */
  private val bound = SettlementTier.VILLAGE.footprintRadius * 0.95 -
      (params.setbackFor(0) + params.lotDepth)

  private fun roll(seed: Long): (Long, Long) -> Double {
    return townRoll(GenRng.hashString("village-$seed"), 0)
  }

  /** A straight road running east, long enough that the village never reaches either end of it. */
  private fun straightRoad(through: Vec2d = centre): Polyline {
    return Polyline(listOf(through - Vec2d(4_000.0, 0.0), through + Vec2d(4_000.0, 0.0)))
  }

  /** The synthesised track, running north so a test can tell it apart from the east-west road. */
  private fun track(): Polyline {
    return Polyline(listOf(centre - Vec2d(0.0, bound), centre + Vec2d(0.0, bound)))
  }

  private fun village(
    buildings: Int,
    roads: List<Polyline> = listOf(straightRoad()),
    track: Polyline = track(),
    form: VillageForm = VillageForm.LINEAR,
    buildable: (Vec2d) -> Boolean = { true },
    seed: Long = 1L
  ): RoadsideVillage? {
    return RoadsideVillage.of(centre, bound, buildings, roads, track, form, buildable, roll(seed), params)
  }

  @Test
  fun `the high street is the road, not a curve laid beside it`() {
    val village = assertNotNull(village(buildings = 18))
    val road = straightRoad()

    val spine = village.segments.filter { it.rank == 0 }
    assertTrue(spine.isNotEmpty(), "a village on a road has a rank-0 street")

    // Every rank-0 end sits on the road's own line, which is the whole claim: the village did not invent a
    // high street, it adopted one.
    for (segment in spine) {
      for (end in listOf(segment.a, segment.b)) {
        assertTrue(
          road.project(end).distance < 0.1,
          "a spine end at $end is ${road.project(end).distance} m off the road"
        )
      }
    }
  }

  @Test
  fun `a settlement it cannot house is refused rather than half built`() {
    // A village at the top of its tier wants far more frontage than a spine and a few lanes carry. Refusing it
    // is what sends it back to the grown layout instead of laying out a third of a settlement.
    assertNull(village(buildings = 200), "a village this size has outgrown the roadside model")
    assertNotNull(village(buildings = 18), "a village this size has not")
  }

  @Test
  fun `a settlement no road reaches is built on its track instead`() {
    // Which is nearly all of them: `SettlementStage` puts only cities and towns on the road network.
    for (roads in listOf(emptyList(), listOf(straightRoad(centre + Vec2d(0.0, bound))))) {
      val village = assertNotNull(
        village(buildings = 18, roads = roads),
        "a village off the road network is still a village"
      )
      val spine = village.segments.filter { it.rank == 0 }
      assertTrue(spine.isNotEmpty(), "it is built along its track")
      assertTrue(
        spine.all { abs((it.b - it.a).normalized().y) > 0.9 },
        "and the track runs north, which is the bearing it was given"
      )
    }
  }

  @Test
  fun `every street stays inside the ground the settlement graded`() {
    for (seed in 1L..40L) {
      val village = village(buildings = 14 + (seed % 9).toInt(), seed = seed) ?: continue
      for (segment in village.segments) {
        for (end in listOf(segment.a, segment.b)) {
          assertTrue(
            end.distanceTo(centre) <= bound + 1.0,
            "seed $seed put a street end ${end.distanceTo(centre)} m out, past the $bound m bound"
          )
        }
      }
    }
  }

  @Test
  fun `the built edge holds every street it was built from`() {
    for (seed in 1L..40L) {
      val village = village(buildings = 14 + (seed % 9).toInt(), seed = seed) ?: continue
      for (segment in village.segments) {
        for (end in listOf(segment.a, segment.b)) {
          assertTrue(village.boundary.contains(end), "seed $seed left a street end outside the boundary")
        }
      }
    }
  }

  @Test
  fun `a crossing road becomes a second high street and a parallel one does not`() {
    val crossing = Polyline(listOf(centre - Vec2d(0.0, 4_000.0), centre + Vec2d(0.0, 4_000.0)))
    val parallel = Polyline(
      listOf(centre + Vec2d(-4_000.0, 40.0), centre + Vec2d(4_000.0, 40.0))
    )

    val junction = assertNotNull(village(buildings = 18, roads = listOf(straightRoad(), crossing)))
    val alongside = assertNotNull(village(buildings = 18, roads = listOf(straightRoad(), parallel)))

    // A crossing road adds street running the other way; a parallel one adds none, because a second high
    // street on the same bearing is the first one drawn twice.
    val northSouth = { v: RoadsideVillage ->
      v.segments.count { it.rank == 0 && abs((it.b - it.a).normalized().y) > 0.9 }
    }
    assertTrue(northSouth(junction) > 0, "a crossroads village has a street running across it")
    assertEquals(0, northSouth(alongside), "a village beside a parallel road has only the one high street")
  }

  @Test
  fun `unbuildable ground shortens the village instead of emptying it`() {
    // A channel across the road east of the centre: the village should stop there and keep its western half.
    val blocked = village(buildings = 18, buildable = { it.x < centre.x + 60.0 })
    val clear = assertNotNull(village(buildings = 18))

    val shortened = assertNotNull(blocked, "ground on one side is still a village")
    assertTrue(
      shortened.segments.size < clear.segments.size,
      "blocked ground should cost streets, not be ignored"
    )
    assertTrue(
      shortened.segments.all { it.a.x < centre.x + 120.0 && it.b.x < centre.x + 120.0 },
      "no street should be laid past the unbuildable ground"
    )
  }

  @Test
  fun `a green village parts around its common and leaves it open`() {
    val village = assertNotNull(village(buildings = 24, form = VillageForm.GREEN))
    assertEquals(VillageForm.GREEN, village.form)

    assertTrue(village.green.size >= 3, "a green village encloses a common")

    val middle = village.green.fold(Vec2d.ZERO) { a, b -> a + b } * (1.0 / village.green.size)
    assertTrue(village.onTheGreen(middle), "the middle of the common is on the common")
    // The common is the open ground *inside* the two arcs, so the arcs themselves are still buildable
    // frontage. A green that swallowed its own carriageway would leave the houses nothing to front onto.
    assertTrue(
      village.segments.none { village.onTheGreen(it.a) || village.onTheGreen(it.b) },
      "no street runs over the common"
    )
  }

  @Test
  fun `the ways outrank the culture when they disagree`() {
    val crossing = Polyline(listOf(centre - Vec2d(0.0, 4_000.0), centre + Vec2d(0.0, 4_000.0)))

    // A crossroads is a crossroads however much its people would rather have had a green.
    val atAJunction = assertNotNull(
      village(buildings = 24, roads = listOf(straightRoad(), crossing), form = VillageForm.GREEN)
    )
    assertEquals(VillageForm.CROSSROADS, atAJunction.form)

    // And a settlement too small to part its way around anything gets the plain form instead.
    val tiny = assertNotNull(village(buildings = 5, form = VillageForm.GREEN))
    assertEquals(VillageForm.LINEAR, tiny.form)
  }

  @Test
  fun `the layout is a pure function of the seed`() {
    val once = assertNotNull(village(buildings = 18, seed = 7L))
    val twice = assertNotNull(village(buildings = 18, seed = 7L))

    assertEquals(once.segments.size, twice.segments.size)
    for (i in once.segments.indices) {
      assertEquals(once.segments[i].a, twice.segments[i].a)
      assertEquals(once.segments[i].b, twice.segments[i].b)
    }
  }
}
