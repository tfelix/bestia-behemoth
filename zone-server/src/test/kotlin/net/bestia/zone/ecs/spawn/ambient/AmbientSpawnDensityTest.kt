package net.bestia.zone.ecs.spawn.ambient

import net.bestia.worldgen.civ.SettlementSpawnPoints
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.zone.bestia.Bestia
import net.bestia.zone.ecs.spawn.WildSpawnConfig
import net.bestia.zone.world.WorldGenConfig
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.exp
import kotlin.math.hypot

/**
 * How thick the wilderness is where a player is standing, measured the way the request was made.
 *
 * ### Why this test exists at all
 *
 * `WildSpawnDensityTest` measured the den layer against the **view volume** - the 352 m of terrain a client
 * holds - and called it the screen. The camera is a spring arm with about sixty metres of ground in frame at
 * full zoom-out, so the box overstates what a player sees by a factor of roughly thirty-four. The dens
 * measured "two to three creatures on screen" and delivered seven hundredths of one, which is why a
 * wilderness that passed every invariant played as empty country.
 *
 * So the two numbers here are the ones the ask was phrased in:
 *
 *  - **creatures on camera**, over [CAMERA_FOOTPRINT_METRES] rather than the stream box, and
 *  - **tiles to the nearest creature**, which is what "one every twenty to forty tiles" means.
 *
 * Mean distance is the honest companion to a mean density, because density alone cannot tell a field from
 * knots: the den layer's 19.8 creatures/km² is a perfectly respectable average made of clumps six hundred
 * metres apart.
 *
 * Figures are **printed rather than pinned**, for `WildSpawnDensityTest`'s reason - a density assertion
 * makes adding a mob a test failure - and the assertions are a wide box around the two ways this can be
 * wrong: country a player can walk across without meeting anything, and a population that has run away.
 */
class AmbientSpawnDensityTest {

  /** The two species that actually ship, transcribed from `resources/mob/`. */
  private val shipped = listOf(
    Bestia(
      id = 1,
      identifier = "blob",
      level = 3,
      experienceReward = 5,
      health = 10,
      mana = 8,
      aiProfile = "passive_wanderer",
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
  fun `a player can see something, and the wilderness has not run away`() {
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

    val ambientConfig = AmbientSpawnConfig()
    val wildConfig = WildSpawnConfig()
    val state = AmbientSiteResolver.State.of(
      generated = generated,
      worldSeed = config.seed,
      catalogue = AmbientSiteResolver.catalogueOf(shipped, wildConfig),
      config = ambientConfig,
      wildConfig = wildConfig
    )

    // Where players actually are: the country a new master arrives in, which is what the request was about.
    val homes = SettlementSpawnPoints.choose(generated)
    val samples = homes.map { Sample(it.name, it.position.x.toLong(), it.position.y.toLong()) }

    println("=== ambient spawn density (seed $DEV_SEED, one site per ${ambientConfig.spacingTiles} tiles) ===")

    val cameraKm2 = (CAMERA_FOOTPRINT_METRES / 1_000.0) * (CAMERA_FOOTPRINT_METRES / 1_000.0)
    var bestOnCamera = 0.0
    var bestSpacing = Double.MAX_VALUE

    for (sample in samples) {
      val tally = AmbientSiteResolver.Tally()
      val sites = sitesAround(state, sample.x, sample.y, tally)
      val windowKm2 = (WINDOW_TILES / 1_000.0) * (WINDOW_TILES / 1_000.0)
      val perKm2 = sites.size / windowKm2
      val onCamera = perKm2 * cameraKm2
      val spacing = meanNearestSpacing(sites)

      bestOnCamera = maxOf(bestOnCamera, onCamera)
      bestSpacing = minOf(bestSpacing, spacing)

      val nearest = if (spacing == Double.MAX_VALUE) "none" else "${"%.0f".format(spacing)} tiles"
      println(
        "  ${sample.name}: ${sites.size} site(s) in ${WINDOW_TILES}x$WINDOW_TILES tiles = " +
            "${"%.0f".format(perKm2)}/km2, nearest creature $nearest away, " +
            "${"%.1f".format(onCamera)} on camera, P(camera empty)=" +
            "${"%.0f".format(exp(-onCamera) * 100)}%"
      )
      println("      $tally")
    }

    println(
      "camera=${CAMERA_FOOTPRINT_METRES.toInt()}m (${"%.5f".format(cameraKm2)} km2). " +
          "WildSpawnDensityTest prints the same figure for the den layer alone, for comparison."
    )
    println(
      "A `no-species` count is the shipped catalogue covering 5 of 21 biomes - missing content that closes " +
          "itself as species are authored, which is why the assertions below take the best starter area " +
          "rather than the average."
    )

    // The two ways this can be wrong. Deliberately wide: the exact figure moves with the catalogue, and the
    // point is that a player standing in starter country meets something without going looking.
    assertTrue(
      bestOnCamera >= 1.0,
      "the best starter country puts only ${"%.2f".format(bestOnCamera)} creature(s) on camera; a player " +
          "would still be walking through empty country"
    )
    assertTrue(
      bestSpacing <= 60.0,
      "the nearest creature in the best starter country is ${"%.0f".format(bestSpacing)} tiles away"
    )
    assertTrue(
      bestSpacing >= 5.0,
      "creatures are ${"%.0f".format(bestSpacing)} tiles apart - the wilderness has run away"
    )
  }

  /** Every site the lattice offers inside a window of [WINDOW_TILES] centred on a point. */
  private fun sitesAround(
    state: AmbientSiteResolver.State,
    x: Long,
    y: Long,
    tally: AmbientSiteResolver.Tally
  ): List<AmbientSite> {
    val half = WINDOW_TILES / 2
    val sites = ArrayList<AmbientSite>()

    val firstX = state.lattice.cellXOf(x - half)
    val lastX = state.lattice.cellXOf(x + half)
    val firstY = state.lattice.cellYOf(y - half)
    val lastY = state.lattice.cellYOf(y + half)

    for (cellY in firstY..lastY) {
      for (cellX in firstX..lastX) {
        AmbientSiteResolver.resolve(state, cellX, cellY, tally)?.let { sites.add(it) }
      }
    }
    return sites
  }

  /**
   * Mean distance from each creature to its nearest neighbour, in tiles.
   *
   * The figure the request was phrased in, and the one a mean density cannot fake: a field of knots has a
   * respectable average density and a nearest-neighbour distance that gives it away.
   */
  private fun meanNearestSpacing(sites: List<AmbientSite>): Double {
    if (sites.size < 2) return Double.MAX_VALUE

    var total = 0.0
    for (site in sites) {
      var nearest = Double.MAX_VALUE
      for (other in sites) {
        if (other === site) continue
        val distance = hypot(
          (other.position.x - site.position.x).toDouble(),
          (other.position.y - site.position.y).toDouble()
        )
        if (distance < nearest) nearest = distance
      }
      total += nearest
    }
    return total / sites.size
  }

  private data class Sample(val name: String, val x: Long, val y: Long)

  private companion object {
    /** `application.yml`'s pinned seed, so this measures the world the dev server actually runs. */
    const val DEV_SEED = 11_753_242L

    /**
     * Ground the camera shows, in metres across. See `WildSpawnDensityTest.cameraFootprintMetres` for the
     * derivation - spring arm 8 to 36 m, 65 degree field of view, pitch capped at -20.
     */
    const val CAMERA_FOOTPRINT_METRES = 60.0

    /** Window measured around each sample point. Wide enough to hold a few hundred sites. */
    const val WINDOW_TILES = 600L
  }
}
