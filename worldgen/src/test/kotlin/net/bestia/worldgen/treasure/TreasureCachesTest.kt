package net.bestia.worldgen.treasure

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.dress.FixtureChannels
import net.bestia.worldgen.dress.FixtureKind
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TreasureCachesTest {

  private val world: GeneratedWorld by lazy {
    TestWorlds.get(WorldConfig(seed = 7L, widthCells = 128, heightCells = 128))
  }

  private val caches by lazy { TreasureCaches(world) }

  private val stored: List<PointMarker> by lazy {
    world.world.features.all().filter { it.kind == FeatureKind.TREASURE_CACHE }.filterIsInstance<PointMarker>()
  }

  @Test
  fun `history buries caches and every hoard holds one`() {
    val hoards = world.world.features.all().count { it.kind == FeatureKind.CAVE_HOARD }
    val tiers = stored.groupingBy { TreasureTier.entries[it.attribute(TreasureChannels.MIN_TIER).toInt()] }.eachCount()
    println("stored caches: ${stored.size}, hoards: $hoards, min tiers: $tiers")

    assertTrue(stored.isNotEmpty(), "no history site buried a cache")
    assertTrue(stored.size >= hoards, "a hoard without a cache")
  }

  @Test
  fun `every cache lies within the depth range`() {
    val params = TreasureParams()
    for (marker in stored) {
      assertTrue(marker.attribute(TreasureChannels.DEPTH) in params.minDepth..params.maxDepth)
    }
  }

  @Test
  fun `the wild lattice is as dense as the design says`() {
    val inland = landSpots().take(40)
    val radius = 500.0
    val counts = inland.map { (x, y) -> caches.near(x, y, radius).count { it.site < 0 } }
    val perKm2 = counts.average() / (Math.PI * radius * radius / 1_000_000.0)
    println("wild caches per km²: $perKm2 over ${counts.size} samples")

    assertTrue(perKm2 in 8.0..40.0, "wild density $perKm2 per km², expected about 16")
  }

  @Test
  fun `wild caches gather around old camps`() {
    val camps = world.world.features.all()
      .filter { it.kind == FeatureKind.FIXTURE }
      .filterIsInstance<PointMarker>()
      .filter { it.attribute(FixtureChannels.KIND).toInt() == FixtureKind.CAMP.ordinal }
    assertTrue(camps.isNotEmpty(), "no camps to gather around")

    val reach = TreasureParams().campReach
    val nearCamps = camps.sumOf { camp -> caches.near(camp.position.x, camp.position.y, reach).count { it.site < 0 } }
    val perKm2 = nearCamps / (camps.size * Math.PI * reach * reach / 1_000_000.0)
    println("wild caches per km² near ${camps.size} camps: $perKm2")

    assertTrue(perKm2 > 40.0, "camps hold $perKm2 per km², expected about 64")
  }

  @Test
  fun `the same query gives the same caches`() {
    val (x, y) = landSpots().first()
    assertEquals(caches.near(x, y, 300.0), TreasureCaches(world).near(x, y, 300.0))
  }

  /** Settlement centres, which are dry land in country people walk through. */
  private fun landSpots(): List<Pair<Double, Double>> {
    return world.world.features.all()
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<PointMarker>()
      .map { it.position.x + 2_000.0 to it.position.y + 2_000.0 }
  }
}
