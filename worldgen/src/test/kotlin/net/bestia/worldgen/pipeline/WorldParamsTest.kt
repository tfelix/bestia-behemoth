package net.bestia.worldgen.pipeline

import net.bestia.worldgen.geo.DetailParams
import net.bestia.worldgen.geo.TectonicsParams
import net.bestia.worldgen.civ.HabitabilityParams
import net.bestia.worldgen.civ.Culture
import net.bestia.worldgen.civ.SettlementParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The four places one number is read by two stages, and therefore the four places a params file would otherwise
 * introduce a silent disagreement.
 *
 * Every one of these agreed for free while all of them defaulted, which is why none of them had a test. Making
 * the params reachable is exactly what removes that guarantee: set the tectonic ocean margin from a file and
 * erosion would go on reapplying the *default* depth, so the margin it restores after two hundred timesteps is
 * not the margin tectonics carved. `WorldParams.resolved` forwards each from the field that owns it, and these
 * are the assertions that say so.
 *
 * Written to fail against a `resolved` that returns `this`, which is the shape the bug would take.
 */
class WorldParamsTest {

  @Test
  fun `erosion reapplies the ocean margin tectonics carved`() {
    val params = WorldParams(tectonics = TectonicsParams(oceanBorderDepth = 900.0, oceanBorderWobble = 1_500.0))

    assertEquals(900.0, params.resolved.erosion.oceanBorderDepth)
    assertEquals(1_500.0, params.resolved.erosion.oceanBorderWobble)
  }

  @Test
  fun `settlement scores sites against the habitability terms the layer was built with`() {
    val params = WorldParams(habitability = HabitabilityParams(culture = Culture.SEAFARING))

    assertEquals(Culture.SEAFARING, params.resolved.settlement.habitability.culture)
  }

  @Test
  fun `the town stage predicts the grading the settlement stage will apply`() {
    val params = WorldParams(settlement = SettlementParams(maxCut = 11.0, maxFill = 7.0))

    assertEquals(11.0, params.resolved.town.grading.maxCut)
    assertEquals(7.0, params.resolved.town.grading.maxFill)
  }

  @Test
  fun `the town stage samples the same detail noise the chunk tier will`() {
    // The sharpest of the four: WorldGround predicts a building's floor from its own WorldHeightField, so a
    // disagreement here puts every building in the world slightly off the ground.
    val params = WorldParams(detail = DetailParams(amplitude = 12.0, wavelength = 300.0))

    assertEquals(12.0, params.resolved.town.detail.amplitude)
    assertEquals(300.0, params.resolved.town.detail.wavelength)
  }

  @Test
  fun `a forwarded value reaches the version, so a propagation can never be invisible`() {
    // The versions are computed from `resolved`, not from the declared fields. Were they computed from the
    // declared ones, changing tectonics' margin would move tectonics' digest and leave erosion's alone - and
    // the cache would serve an erosion pass that ran with different numbers.
    val plain = WorldParams()
    val deeper = WorldParams(tectonics = TectonicsParams(oceanBorderDepth = 900.0))

    assertNotEquals(plain.version, deeper.version)
    assertNotEquals(
      plain.resolved.erosion.digest().value,
      deeper.resolved.erosion.digest().value,
      "erosion's own digest has to move, or its cache key does not"
    )
  }

  @Test
  fun `the chunk tier reaches a version at all`() {
    // It reached none before this existed: the chunk cache key is (seed, pipelineVersion, chunk), and the
    // detail noise, the rock column and the droplet field were invisible to all three version numbers.
    val plain = WorldParams()

    assertNotEquals(
      plain.chunkTierVersion,
      WorldParams(detail = DetailParams(amplitude = 12.0)).chunkTierVersion
    )
    assertNotEquals(
      StandardWorld.pipeline(config()).pipelineVersion,
      StandardWorld.pipeline(config(), WorldParams(detail = DetailParams(amplitude = 12.0))).pipelineVersion,
      "a chunk-tier retune has to move pipelineVersion, or the chunk cache serves the old ground"
    )
  }

  @Test
  fun `the defaults are pinned`() {
    // Moved with `ResourceParams.ore` and with ruby's and diamond's retuning; see ParamsVersionTest's own pins.
    assertEquals(-1_090_744_351_799_380_755L, WorldParams.DEFAULT.version, "re-pin: the world tuning moved")
    // Moved with `ChunkMaterializer.VERSION` 1 -> 2: buildings stopped being voxels and the strata draw
    // collapsed to STONE plus LIMESTONE, both of which change what a column materialises into.
    assertEquals(486_565_586_489_113_592L, WorldParams.DEFAULT.chunkTierVersion, "re-pin: the chunk tier moved")
  }

  private fun config() = StandardWorld.demoConfig().copy(widthCells = 64, heightCells = 64)
}
