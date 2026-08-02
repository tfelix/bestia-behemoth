package net.bestia.worldgen.civ

import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.FootprintFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Town quarters: the polygon over a group of plots that replaced the deleted street-graph block.
 *
 * Against a real world, because every claim here is about the relationship between a hull and the buildings it
 * was grown from, and a fixture would have to invent both sides of it.
 */
class DistrictTest {

  private val generated: GeneratedWorld by lazy {
    StandardWorld.build(StandardWorld.demoConfig(seed = 909L).copy(widthCells = 160, heightCells = 160))
  }

  private val districts: List<AreaFeature> by lazy {
    generated.world.features.all()
      .filter { it.kind == FeatureKind.DISTRICT }
      .filterIsInstance<AreaFeature>()
  }

  private val buildings: List<FootprintFeature> by lazy {
    generated.world.features.all()
      .filter { it.kind == FeatureKind.BUILDING }
      .filterIsInstance<FootprintFeature>()
  }

  @Test
  fun `the world has districts at all`() {
    // Habit six. Every test below iterates this list, and a producer that emits nothing passes all of them.
    // Its predecessor - blocks as faces of the street graph - is exactly the subsystem that shipped complete,
    // tested and reaching 68 plots where it should have reached 574.
    assertTrue(districts.isNotEmpty(), "seed 909 laid out ${buildings.size} buildings and no district")
  }

  @Test
  fun `a district is a query surface and never touches the ground`() {
    // The reason `AreaFeature.profile` is nullable, asserted rather than assumed. A district with a profile
    // would be a town-sized terrace, and it would reach the chunk tier's height path on every column it
    // covers - so this is also what keeps a quarter out of `GlacialStage.carveInto`'s reach for free.
    for (district in districts) {
      assertTrue(!district.affectsHeight, "$district has a profile, so a quarter is levelling the town")
    }
  }

  @Test
  fun `a district holds every building it was grown from`() {
    // The claim the hull has to earn. It is a convex hull of building corners, pushed outward, and then
    // *simplified* down to `Ring.MAX_VERTICES` - and simplification cuts corners off, each cut moving the
    // boundary inward across ground that had a building on it. Counted per quarter rather than in total, so
    // that buildings of an interleaving quarter cannot make up a shortfall.
    for (district in districts) {
      val kind = DistrictKind.entries[district.attribute(DistrictChannels.KIND).toInt()]
      val claimed = district.attribute(DistrictChannels.BUILDINGS).toInt()

      val inside = generated.world.features.query(district.bbox)
        .filterIsInstance<FootprintFeature>()
        .filter { it.kind == FeatureKind.BUILDING }
        .filter { district.contains(it.center.x, it.center.y) }
        .count {
          val function = BuildingFunction.entries[it.attribute(BuildingChannels.FUNCTION).toInt()]
          Districts.quarterOf(function) == kind
        }

      assertTrue(
        inside >= claimed,
        "$district claims $claimed $kind buildings and its ring contains $inside of them"
      )
    }
  }

  @Test
  fun `a handful of neighbours is not a quarter`() {
    for (district in districts) {
      val claimed = district.attribute(DistrictChannels.BUILDINGS).toInt()
      assertTrue(
        claimed >= Districts.MIN_BUILDINGS,
        "$district was grown from $claimed buildings, below the floor of ${Districts.MIN_BUILDINGS}"
      )
    }
  }

  @Test
  fun `every district belongs to a settlement that exists`() {
    val settlements = generated.world.features.all()
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<net.bestia.worldgen.vector.PointMarker>()
      .map { it.attribute(SettlementChannels.INDEX).toInt() }
      .toSet()

    for (district in districts) {
      val index = district.attribute(DistrictChannels.SETTLEMENT).toInt()
      assertTrue(index in settlements, "$district belongs to settlement $index, which is not in the world")
    }
  }

  @Test
  fun `a town has more than one kind of quarter`() {
    // What a district is *for*: a position in a town answers "market" or "craft" rather than "town". A world
    // whose districts were all residential would satisfy every other test here and be worth nothing.
    val byKind = districts.groupingBy { it.attribute(DistrictChannels.KIND).toInt() }.eachCount()
    assertTrue(byKind.size >= 3, "the whole world has only ${byKind.size} kinds of quarter: $byKind")

    val market = DistrictKind.MARKET.ordinal
    assertTrue((byKind[market] ?: 0) > 0, "no town in the world has a market quarter")
  }

  @Test
  fun `the quarter mapping covers every building function`() {
    // `Districts.quarterOf` is exhaustive by construction - it is a `when` with no `else` - so this asserts
    // the other half: that only the one function deliberately left out is left out.
    val unplaced = BuildingFunction.entries.filter { Districts.quarterOf(it) == null }
    assertEquals(
      listOf(BuildingFunction.FORTIFICATION), unplaced,
      "a building function belongs to no quarter, so its buildings are in no district"
    )
  }
}
