package net.bestia.worldgen.bio

import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Parallel
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.voxel.SurfaceSampler
import net.bestia.worldgen.voxel.VegetationParams
import net.bestia.worldgen.voxel.VegetationScatter

/**
 * Stage 7: how much of each cell is under a leaf canopy.
 *
 * ### A stage that stores a summary of something it does not store
 *
 * The trees themselves are never stored - a world of five hundred kilometres holds on the order of a billion
 * of them, so `voxel/VegetationScatter.kt` is a *function* evaluated per column at chunk generation and this
 * is a kilometre-scale average of that same function. Nothing here decides anything the chunk tier will not
 * decide again for itself; the point is that "how wooded is it here" becomes answerable without materialising
 * a million voxels to find out.
 *
 * ### Why it is not a second density model
 *
 * The obvious shape for this stage is a rule of its own - some function of biome and rainfall producing a
 * plausible cover fraction. That is two things that mean the same, and two things that mean the same
 * disagree. So the stage constructs the *same* [VegetationScatter] the materialiser will, from the *same*
 * [SurfaceSampler.of] layers, and averages
 * [VegetationScatter.coverOf] over sub-samples of each cell.
 * `Invariants.checkCanopyCoverAgreesWithTheBiome` is what says so out loud.
 *
 * ### Two nested sub-samplings, because the two terms vary at different scales
 *
 * The density has a site term - biome and soil - and a patch term, and they need different sample counts for
 * the same reason: a kilometre cell contains about seven cycles of the patch field, but the *biome* varies
 * inside a cell only near an ecotone, where `SurfaceSampler.biomeAt`'s dither lets the runner-up win
 * individual positions on a fourteen-metre field. Sampling either one once per cell prints a map of the
 * sampling rather than of the world, and the two failures look different: the patch term made the whole map
 * grainy, and the site term made every biome boundary a spray of loose pixels.
 *
 * So the site term is sampled on a [SITE_SAMPLES] grid and the patch on a [PATCH_SAMPLES] grid inside each of
 * those, which is `SITE_SAMPLES * PATCH_SAMPLES` patch draws per axis for sixteen of the expensive one.
 * Averaging the dither is also the *more correct* answer, not merely the prettier one: a cell that is half
 * forest and half grassland genuinely carries half a forest's canopy, and one draw of a coin cannot say so.
 */
class VegetationStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: VegetationParams = VegetationParams()
) : Stage {

  override val id = ID
  override val version = 1

  /**
   * The catalogue is folded as well as the params, and it is not optional.
   *
   * `Biome.canopy` decides where every tree in the world stands and lives in an enum rather than in
   * [VegetationParams], so without this a designer could retune the whole forest cover of a world and move
   * no version number at all. Same reason `ResourceStage` folds `ResourceType.catalogueDigest`.
   */
  override val paramsVersion get() = GenRng.hash(params.digest().value, Biomes.catalogueDigest())

  /**
   * Everything [SurfaceSampler.of] reads, and nothing else.
   *
   * Biomes for the litter term and the soil depth; hydrology for the water level and the lake ids; climate
   * for the temperature. A missing declaration is not a silent wrong answer here - `ScopedLayerStore` refuses
   * the read - which is the whole reason the sampler is built through a factory rather than layer by layer.
   */
  override val dependencies = listOf(ClimateStage.ID, HydrologyStage.ID, BiomeStage.ID)
  override val scale = StageScale.WORLD

  override val outputs = listOf(StageOutput.Raster(LayerId.CANOPY_COVER))

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val scatter = VegetationScatter(
      config = ctx.config,
      surface = SurfaceSampler.of(ctx.layers, ctx.config),
      seed = ctx.config.seed,
      params = params
    )

    val metres = region.resolution.metresPerCell
    val siteStep = metres / SITE_SAMPLES
    val patchStep = siteStep / PATCH_SAMPLES
    val maxDensity = params.maxDensity
    val samples = (SITE_SAMPLES * SITE_SAMPLES * PATCH_SAMPLES * PATCH_SAMPLES).toDouble()
    val cover = Grid(region.width, region.height)

    // Every cell is an independent average over read-only fields, so the rows split cleanly. The sum inside
    // one cell stays on one thread, which is what `Parallel`'s no-accumulator rule asks for.
    Parallel.rows(region.height, region.width) { yFrom, yUntil ->
      for (y in yFrom until yUntil) {
        for (x in 0 until region.width) {
          val originX = (region.minX + x) * metres
          val originY = (region.minY + y) * metres
          var sum = 0.0

          for (siteY in 0 until SITE_SAMPLES) {
            for (siteX in 0 until SITE_SAMPLES) {
              val site = scatter.siteDensityAt(
                originX + (siteX + 0.5) * siteStep,
                originY + (siteY + 0.5) * siteStep
              )
              // Ocean, ice, desert and bare rock, which is most of a world. Nothing below it could produce
              // anything but zero, and skipping them is what pays for the sample counts.
              if (site <= 0.0) continue

              for (patchY in 0 until PATCH_SAMPLES) {
                val worldY = originY + siteY * siteStep + (patchY + 0.5) * patchStep
                for (patchX in 0 until PATCH_SAMPLES) {
                  val worldX = originX + siteX * siteStep + (patchX + 0.5) * patchStep
                  val density = (site * scatter.patchAt(worldX, worldY)).coerceAtMost(maxDensity)
                  // The cover conversion inside the average, not outside it. Cover is concave in density, so
                  // converting the mean would report a wood with a clearing in it as denser than it is.
                  sum += scatter.coverOf(density)
                }
              }
            }
          }

          cover.data[y * region.width + x] = sum / samples
        }
      }
    }

    return StageResult.of(cover.toLayer(LayerId.CANOPY_COVER, region))
  }

  companion object {
    val ID = StageId("vegetation")

    /**
     * Samples of the biome and soil term per axis per cell.
     *
     * The expensive one - `SurfaceSampler.biomeAt` is a warp plus a dither, five gradient evaluations - so it
     * gets the smaller grid. Sixteen draws is enough to turn the dither at an ecotone into a blend instead of
     * a coin flip, which is all it is there to do.
     */
    const val SITE_SAMPLES = 4

    /**
     * Samples of the patch field per axis inside each site sample.
     *
     * Twelve per axis over the whole cell, measured rather than argued: a hundred and forty-four draws of a
     * field with a standard deviation near 0.2 gives a standard error under 0.02, which is where the cell
     * grid stops being visible in the exported map. Four was the first attempt and printed a picture of its
     * own sampling noise.
     */
    const val PATCH_SAMPLES = 3
  }
}
