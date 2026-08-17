package net.bestia.zone.ecs.spawn

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.civ.SettlementSpawnPoints
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.spawn.SpawnerChannels
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.bestia.Bestia
import net.bestia.zone.ecs.spawn.WildSpawnerService.Candidate
import net.bestia.zone.world.WorldGenConfig
import net.bestia.zone.world.stream.ChunkStreamConfig
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.exp
import kotlin.math.hypot

/**
 * How thick the wilderness actually is, in creatures a player can see.
 *
 * `WildDenCoverageTest`'s idea - a content measurement wearing a test's clothes - pointed at the question
 * that measurement could not answer. A perfectly shaped level ramp over 656 dens spread across 16 384 km²
 * passes every invariant in the generator and still produces a world a player can walk across without
 * meeting anything, which is what the shipped world did: the nearest stockable den to a master spawn point
 * was 1 450 m away against a 220 m activation range.
 *
 * The figures are **printed rather than asserted**, for `WildDenCoverageTest`'s reason: pinning a density
 * would make adding a mob a test failure. The assertions are a wide box around catastrophe - an empty
 * wilderness on one side, a runaway marker count on the other.
 *
 * ### Two density numbers, and why both
 *
 * Habitat is the constraint the selection never relaxes, and the shipped catalogue's only ordinary species
 * covers five biomes out of twenty-one - about half the dens the generator places. So the world-wide figure
 * reads roughly half the in-habitat one, and that gap is **missing content, not a broken join**. Printing
 * only the world-wide number would make the wilderness look under-tuned; printing only the in-habitat one
 * would hide that half the map is quiet. The gap closes as species are authored, with no code change.
 */
class WildSpawnDensityTest {

  /** The two species that actually ship, transcribed from `resources/mob/`. */
  private val shipped = listOf(
    Bestia(
      id = 1,
      identifier = "blob",
      level = 3,
      experienceReward = 5,
      health = 10,
      mana = 8,
      habitat = "GRASSLAND,DRYLAND,RIPARIAN,BEACH,TEMPERATE_FOREST",
      spawnWeight = 100,
      temperatureMinCelsius = -5.0,
      temperatureMaxCelsius = 32.0
    ),
    Bestia(
      id = 2,
      identifier = "doom_master_of_doom",
      level = 100,
      experienceReward = 12_000,
      health = 4_000,
      mana = 900,
      corruptedOnly = true,
      boss = true,
      spawnWeight = 1
    )
  )

  @Test
  fun `the wilderness is thick enough to walk into and not so thick it runs away`() {
    val settings = WorldGenConfig()
    val config = WorldConfig(
      seed = DEV_SEED,
      widthCells = settings.widthCells,
      heightCells = settings.heightCells,
      baseResolution = Resolution(settings.cellSizeMetres),
      seaLevel = settings.seaLevelMetres,
      chunkSize = settings.chunkSize,
      chunkHeight = settings.chunkHeight,
      voxelSize = settings.voxelSizeMetres,
      wrapX = settings.wrapX,
      wrapY = settings.wrapY
    )
    val generated = StandardWorld.build(config)
    val spawnConfig = WildSpawnConfig()

    val stocking = WildSpawnerService.stock(
      generated, config.seed, WORLD_ID, shipped.map(::Candidate), spawnConfig
    )

    // Derived from the real configs rather than restated, so the test cannot drift away from what the client
    // is actually streamed.
    val stream = ChunkStreamConfig()
    val screenMetres = stream.chunksAcrossView * settings.chunkSize * settings.voxelSizeMetres
    val screenKm2 = (screenMetres / 1_000.0) * (screenMetres / 1_000.0)

    val worldKm2 = (settings.widthCells * settings.cellSizeMetres / 1_000.0) *
        (settings.heightCells * settings.cellSizeMetres / 1_000.0)

    val densPerKm2 = stocking.dens.size / worldKm2
    val mobs = stocking.dens.sumOf { it.pack }
    val mobsPerKm2 = mobs / worldKm2
    val meanRange = if (stocking.dens.isEmpty()) 0.0 else stocking.dens.map { it.range }.average()

    // A den contributes to a screen when its spawn box overlaps it, so its centre may lie anywhere in a
    // square of side `screen + range`. This is the clumping figure: the mean alone cannot tell a field of
    // creatures from knots of six with empty country between them, and the radius is the knob that decides
    // which one it is.
    val reach = (screenMetres + meanRange) / 1_000.0

    // The honest pair: the biomes the catalogue can actually stock, against the whole world.
    val coveredBiomes = shipped.flatMap { it.habitat.split(',') }.filter { it.isNotBlank() }.toSet()
    val coveredShare = coveredShareOfDens(generated, coveredBiomes)
    val mobsPerKm2InHabitat = if (coveredShare <= 0.0) 0.0 else mobsPerKm2 / coveredShare

    val densPerScreen = densPerKm2 * reach * reach
    val densPerScreenInHabitat = if (coveredShare <= 0.0) 0.0 else densPerScreen / coveredShare
    val emptyScreenShare = exp(-densPerScreenInHabitat)

    println("=== wild spawn density (seed $DEV_SEED, ${settings.widthCells}x${settings.heightCells} km) ===")
    println(
      "markers=${stocking.markers}  stocked=${stocking.dens.size}  thinned=${stocking.thinned}  " +
          "unfilled=${stocking.unfilled}  clamped=${stocking.clamped}"
    )
    println(
      "fallbacks=${stocking.fallbacks} by band 1-8/9-40/41-79/80-100 " +
          "${stocking.fallbacksByBand.joinToString("/")}  worst miss=${stocking.worstMiss}"
    )
    println("dens/km2=${"%.2f".format(densPerKm2)}  creatures=$mobs  mobs/km2=${"%.1f".format(mobsPerKm2)}")
    println(
      "mobs/km2 within stockable biomes=${"%.1f".format(mobsPerKm2InHabitat)} " +
          "(catalogue covers ${"%.0f".format(coveredShare * 100)}% of dens; the rest is missing content)"
    )
    println(
      "screen=${screenMetres.toInt()}m (${"%.4f".format(screenKm2)} km2)  " +
          "expected mobs on screen=${"%.1f".format(mobsPerKm2 * screenKm2)}  " +
          "within stockable biomes=${"%.1f".format(mobsPerKm2InHabitat * screenKm2)}"
    )
    println(
      "mean spawn radius=${meanRange.toInt()}m  dens contributing per screen=${"%.2f".format(densPerScreen)}  " +
          "within stockable biomes=${"%.2f".format(densPerScreenInHabitat)}  " +
          "P(stockable screen holds nothing)=${"%.0f".format(emptyScreenShare * 100)}%"
    )

    val index = generated.world.features.indexMetrics()
    println("feature index: $index")

    println("--- distance from each master spawn point to the nearest stocked den ---")
    var nearestOverall = Double.MAX_VALUE
    for (candidate in SettlementSpawnPoints.choose(generated)) {
      val px = candidate.position.x / config.voxelSize
      val py = candidate.position.y / config.voxelSize
      val nearest = stocking.dens.minOfOrNull { hypot(it.position.x - px, it.position.y - py) } ?: Double.NaN
      if (nearest.isFinite()) nearestOverall = minOf(nearestOverall, nearest)
      println("  ${candidate.name}: ${nearest.toInt()} m")
    }

    // Asserted on the in-habitat figures throughout, not the world-wide ones. The gap between them is the
    // biomes no shipped species tolerates, which is missing content that closes itself as mobs are authored
    // - asserting on the world-wide number would be asserting on how much content exists, and would fail
    // for a reason no change to this subsystem can fix.
    assertTrue(stocking.dens.isNotEmpty(), "not one den on the world could be stocked")
    assertTrue(
      mobsPerKm2InHabitat > 10.0,
      "country the catalogue can stock holds only ${"%.1f".format(mobsPerKm2InHabitat)} creatures/km2"
    )
    assertTrue(
      mobsPerKm2InHabitat < 200.0,
      "the wilderness has run away at ${"%.1f".format(mobsPerKm2InHabitat)} creatures/km2"
    )
    assertTrue(
      stocking.markers < 200_000,
      "${stocking.markers} markers - generation cost has run away; check SpawnerParams.candidateSpacing"
    )
    assertTrue(
      densPerScreenInHabitat > 0.9,
      "only ${"%.2f".format(densPerScreenInHabitat)} dens reach the average stockable screen; the field is " +
          "knots with empty ground between them rather than populated country. `radius-multiplier` is the knob."
    )
    assertTrue(
      nearestOverall < 1_000.0,
      "the nearest stocked den to any master spawn point is ${nearestOverall.toInt()} m away; a new master " +
          "would have to go looking. This was 1450 m before the density retune."
    )
    assertTrue(index.oversizedCount < 50, "${index.oversizedCount} features overflow the index grid")
  }

  /** Share of the generator's dens standing in a biome the catalogue can stock at all. */
  private fun coveredShareOfDens(generated: GeneratedWorld, coveredBiomes: Set<String>): Double {
    var total = 0
    var covered = 0
    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.BESTIA_SPAWN) continue
      val marker = feature as? PointMarker ?: continue
      total++
      if (Biome.entries[marker.attribute(SpawnerChannels.BIOME).toInt()].name in coveredBiomes) covered++
    }
    return if (total == 0) 0.0 else covered.toDouble() / total
  }

  private companion object {
    /** `application.yml`'s pinned seed, so this measures the world the dev server actually runs. */
    const val DEV_SEED = 11_753_242L
    const val WORLD_ID = 1L
  }
}
