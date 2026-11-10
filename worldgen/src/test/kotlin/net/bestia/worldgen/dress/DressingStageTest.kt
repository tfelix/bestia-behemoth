package net.bestia.worldgen.dress

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.voxel.PropFlags
import net.bestia.worldgen.voxel.PropKind
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The set dressing exists, and the chunk tier turns it into props.
 *
 * Counted per kind, on the habit-6 reason: a producer that never fires - a pier search that looks in the wrong
 * place, a junction test that matches nothing - passes every structural check. Seed 7 at 128 cells has coast,
 * hill country, villages and towns, so the kinds every such world must have are asserted and the rest printed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DressingStageTest {

  private val world: GeneratedWorld by lazy {
    TestWorlds.get(WorldConfig(seed = 7L, widthCells = 128, heightCells = 128))
  }

  private val fixtures: List<PointMarker> by lazy {
    world.world.features.all().filter { it.kind == FeatureKind.FIXTURE }.filterIsInstance<PointMarker>()
  }

  private fun kindOf(marker: PointMarker) = FixtureKind.entries[marker.attribute(FixtureChannels.KIND).toInt()]

  @Test
  fun `every kind of set dressing is placed`() {
    val counts = FixtureKind.entries.associateWith { kind -> fixtures.count { kindOf(it) == kind } }
    val cemeteries = world.world.features.all().count { it.kind == FeatureKind.CEMETERY }
    println("fixture markers: " + counts.entries.joinToString { "${it.key}=${it.value}" } + ", cemeteries=$cemeteries")

    for (kind in REQUIRED) {
      assertTrue(counts.getValue(kind) > 0, "no $kind on a world with settlements, roads and coast")
    }
    assertTrue(cemeteries > 0, "no cemetery anywhere")
  }

  @Test
  fun `water fixtures stand on the water and nothing else does`() {
    for (marker in fixtures) {
      val elevation = marker.attribute(FixtureChannels.ELEVATION)
      when (kindOf(marker)) {
        FixtureKind.PIER -> assertTrue(elevation.isFinite(), "a pier with no deck elevation")
        FixtureKind.BOAT -> Unit
        else -> assertTrue(elevation.isNaN(), "${kindOf(marker)} carries an elevation of its own")
      }
    }
  }

  @Test
  fun `lanterns are numbered along their line`() {
    val bySettlement = fixtures.filter { kindOf(it) == FixtureKind.LANTERN }
      .groupBy { it.attribute(FixtureChannels.SETTLEMENT).toInt() }
    assertTrue(bySettlement.isNotEmpty(), "no lanterns")
    for ((settlement, lanterns) in bySettlement) {
      val orders = lanterns.map { it.attribute(FixtureChannels.ORDER).toInt() }.sorted()
      assertTrue(orders == orders.indices.toList(), "settlement $settlement's lanterns are not numbered 0..n: $orders")
    }
  }

  /**
   * A waterfront settlement gets a pier with a boat at it, its deck above the water.
   *
   * Pinned to seed 3, which has a town near enough the sea: settlements in this generator rarely stand on the
   * shore, so most seeds legitimately have none, and a check that passes vacuously on them proves nothing.
   */
  @Test
  fun `a waterfront settlement has a pier with a boat at it`() {
    val coastal = TestWorlds.get(WorldConfig(seed = 3L, widthCells = 128, heightCells = 128))
    val markers = coastal.world.features.all().filter { it.kind == FeatureKind.FIXTURE }.filterIsInstance<PointMarker>()
    val piers = markers.filter { kindOf(it) == FixtureKind.PIER }
    val boats = markers.filter { kindOf(it) == FixtureKind.BOAT }
    println("seed 3: ${piers.size} piers, ${boats.size} boats")
    assertTrue(piers.isNotEmpty(), "no pier on a world with a waterfront town")
    assertTrue(boats.isNotEmpty(), "piers with no boats")
    for (pier in piers) {
      val deck = pier.attribute(FixtureChannels.ELEVATION)
      assertTrue(deck > coastal.config.seaLevel - 0.01, "a pier deck at $deck stands below the sea at ${coastal.config.seaLevel}")
    }
  }

  /** Around a village: its fields fenced and cropped, its cemetery walled and filled. */
  @Test
  fun `the chunk tier fences fields and fills cemeteries`() {
    val cemetery = world.world.features.all().filter { it.kind == FeatureKind.CEMETERY }
      .filterIsInstance<AreaFeature>().first()
    val field = world.world.features.all().filter { it.kind == FeatureKind.FIELD }
      .filterIsInstance<AreaFeature>().first()

    val counts = HashMap<FixtureKind, Int>()
    for (area in listOf(cemetery, field)) {
      val box = area.ring.bbox
      for (cx in world.config.chunkOf(box.minX)..world.config.chunkOf(box.maxX)) {
        for (cy in world.config.chunkOf(box.minY)..world.config.chunkOf(box.maxY)) {
          val props = world.propsIn(cx, cy)
          for (i in props.indices) {
            if (props.kindAt(i) != PropKind.FIXTURE) continue
            val kind = FixtureKind.entries[props.subKindAt(i)]
            counts[kind] = (counts[kind] ?: 0) + 1
            assertTrue(PropFlags.fixtureStyleOf(props.flagsAt(i)) in 0..31)
          }
        }
      }
    }
    println("fixture props around a cemetery and a field: $counts")
    assertTrue((counts[FixtureKind.FENCE] ?: 0) > 0, "no fence round a field or a cemetery")
    assertTrue((counts[FixtureKind.GRAVESTONE] ?: 0) + (counts[FixtureKind.NAMED_GRAVE] ?: 0) > 0, "an empty cemetery")
  }

  private companion object {
    val REQUIRED = listOf(
      FixtureKind.WELL,
      FixtureKind.LANTERN,
      FixtureKind.MILESTONE,
      FixtureKind.WINDMILL,
      FixtureKind.CAMP
    )
  }
}
