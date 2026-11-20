package net.bestia.worldgen.stronghold

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.history.Names
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.worldgen.pipeline.WorldParams
import net.bestia.worldgen.resource.DepositChannels
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Seeds on the genesis-sized worlds other tests already build, so this class generates nothing of its own. */
class EmpyriteSeedStageTest {

  private val params = WorldParams.DEFAULT.empyriteSeed

  @Test
  fun `every world wakes at least the four seeds the zone starts with`() {
    for (world in worlds) {
      assertTrue(seedsOf(world).size >= MIN_AWAKE, "seed ${world.config.seed} holds ${seedsOf(world).size} seeds")
    }
  }

  @Test
  fun `seeds are ranked by the worth of the ore around them`() {
    for (world in worlds) {
      val seeds = seedsOf(world)
      assertEquals(seeds.indices.toList(), seeds.map { it.rank })
      assertEquals(seeds.map { it.worth }.sortedDescending(), seeds.map { it.worth })
      assertTrue(seeds.all { it.worth > 0.0 }, "a seed with no ore around it on seed ${world.config.seed}")
    }
  }

  @Test
  fun `no ore body reaches into a seed's grounds`() {
    for (world in worlds) {
      val deposits = world.world.features.all().filter { it.kind == FeatureKind.ORE_DEPOSIT }.filterIsInstance<PointMarker>()
      for (seed in seedsOf(world)) {
        val nearest = deposits.minOf { it.position.distanceTo(seed.marker.position) - it.attribute(DepositChannels.RADIUS) }
        assertTrue(nearest >= params.groundsRadius, "ore ${nearest.toInt()} m from seed ${seed.rank}")
      }
    }
  }

  @Test
  fun `seeds keep away from each other and from every settlement`() {
    for (world in worlds) {
      val seeds = seedsOf(world)
      val settlements = world.world.features.all().filter { it.kind == FeatureKind.SETTLEMENT }.filterIsInstance<PointMarker>()
      for (seed in seeds) {
        val at = seed.marker.position
        assertTrue(settlements.all { it.position.distanceTo(at) >= params.settlementClearance }, "seed ${seed.rank} by a town")
        assertTrue(
          seeds.filter { it !== seed }.all { it.marker.position.distanceTo(at) >= params.seedSeparation },
          "seed ${seed.rank} by another seed"
        )
      }
    }
  }

  @Test
  fun `seeds stand on dry land`() {
    for (world in worlds) {
      val elevation = world.world.layers.require<FloatLayer>(LayerId.ELEVATION)
      for (seed in seedsOf(world)) {
        val at = seed.marker.position
        assertTrue(elevation.sampleBilinear(at.x, at.y) - world.config.seaLevel >= params.freeboard, "seed ${seed.rank} is wet")
      }
    }
  }

  @Test
  fun `every seed carries a name no settlement and no other seed has`() {
    for (world in worlds) {
      val settlementNames = world.world.chronicle.settlements.map { Names.place(it.nameSeed, it.nameCulture.coerceAtLeast(0)) }
      val seedNames = seedsOf(world).map { Names.place(it.nameSeed, it.culture) }
      assertEquals(seedNames.distinct(), seedNames, "two seeds share a name on seed ${world.config.seed}")
      assertTrue(seedNames.none { it in settlementNames }, "a seed is named like a town on seed ${world.config.seed}")
    }
  }

  private class Seed(val marker: PointMarker) {
    val rank = marker.attribute(EmpyriteSeedChannels.RANK).toInt()
    val worth = marker.attribute(EmpyriteSeedChannels.WORTH)
    val nameSeed = marker.attribute(EmpyriteSeedChannels.NAME_SEED).toLong()
    val culture = marker.attribute(EmpyriteSeedChannels.CULTURE).toInt()
  }

  private fun seedsOf(world: GeneratedWorld): List<Seed> {
    return world.world.features.all()
      .filter { it.kind == FeatureKind.EMPYRITE_SEED }
      .filterIsInstance<PointMarker>()
      .map { Seed(it) }
      .sortedBy { it.rank }
  }

  private companion object {

    /** The zone wakes at least this many, whatever the number of guilds. */
    const val MIN_AWAKE = 4

    val worlds: List<GeneratedWorld> by lazy {
      listOf(3L, 7L).map { TestWorlds.get(WorldConfig(seed = it, widthCells = 128, heightCells = 128)) }
    }
  }
}
