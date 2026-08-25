package net.bestia.worldgen.lod

import net.bestia.worldgen.core.BaseHeightField
import net.bestia.worldgen.core.FeatureStore
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.FeatureEvaluator
import net.bestia.worldgen.voxel.SurfaceCover
import net.bestia.worldgen.voxel.SurfaceSampler
import net.bestia.worldgen.voxel.VegetationScatter
import kotlin.math.roundToInt

/**
 * Samples the visible surface of a [PatchPos] straight off the world tier, without materialising anything.
 *
 * ### Why this is cheap enough to widen the draw distance with
 *
 * It is the *height half* of chunk generation and nothing else. `ChunkHeightSampler` already separates that
 * half out - base heightfield plus every vector feature reaching the column - and `ChunkMaterializer.surface`
 * is public precisely so "what does the ground here read as" can be asked without building a chunk to find
 * out. Everything expensive in materialisation - strata, caves, ore, structures, scatter - is below the
 * surface or too small to see, and none of it runs here.
 *
 * The two savings compound. Sampling every fourth metre instead of every metre is a sixteenth of the height
 * evaluations, and doing it over a whole patch is **one** feature query where the sixty-four chunks inside it
 * would be sixty-four - which is the half that matters, since `ChunkService` documents the query as costing
 * the same as the thousand heights it serves.
 *
 * ### Safe to run off the tick thread
 *
 * Holds no mutable state of its own. The layers are plain arrays, the feature index is frozen once generation
 * finishes, and the [FeatureEvaluator] - which does carry scratch - is built fresh inside each call. This is
 * the same guarantee `TileInputs` relies on to render map tiles on their own pool, and it is what keeps a
 * burst of patch requests off the zone thread entirely.
 */
class SurfacePatchSampler(
  private val config: WorldConfig,
  private val base: BaseHeightField,
  private val features: FeatureStore,
  private val surface: SurfaceSampler,
  private val vegetation: VegetationScatter
) {

  fun sample(pos: PatchPos): SurfacePatch {
    // The index already stores each feature's bounds expanded by its own influence radius, so the patch's
    // own bounds are enough - a miss cannot reach a sample inside them.
    val evaluator = FeatureEvaluator(features.query(pos.bounds))

    val count = PatchGrid.SAMPLES * PatchGrid.SAMPLES
    val height = FloatArray(count)
    val water = FloatArray(count)
    val block = ByteArray(count)
    val canopy = ByteArray(count)

    for (j in 0 until PatchGrid.SAMPLES) {
      val worldY = pos.worldY(j)

      for (i in 0 until PatchGrid.SAMPLES) {
        val worldX = pos.worldX(i)
        val at = SurfacePatch.index(i, j)

        val ground = evaluator.heightAt(worldX, worldY, base.heightAt(worldX, worldY))
        val waterLevel = surface.waterLevelAt(worldX, worldY)
        val depth = (waterLevel - ground).coerceAtLeast(0.0)

        height[at] = ground.toFloat()
        water[at] = if (depth > 0.0) waterLevel.toFloat() else SurfacePatch.NO_WATER

        // The same function `ChunkMaterializer.fillColumn` picks a column's top voxel with, so a patch and
        // the chunk that later replaces it name the same material rather than two plausible ones.
        block[at] = SurfaceCover.cap(
          biome = surface.biomeAt(worldX, worldY),
          temperature = surface.temperatureAt(worldX, worldY),
          waterDepth = depth,
          blighted = surface.isBlightedAt(worldX, worldY)
        ).id.toByte()

        // Cover rather than tree positions. A view volume holds thousands of trees and streaming them is
        // already the second quadratic cost in this subsystem; one byte per sample lets a client scatter its
        // own impostors instead, which at this range is a question of how dark the wood looks.
        canopy[at] = (vegetation.coverAt(worldX, worldY).coerceIn(0.0, 1.0) * 255.0).roundToInt().toByte()
      }
    }

    return SurfacePatch(pos, height, water, block, canopy)
  }

  companion object {

    /**
     * The sampler for a generated world.
     *
     * Takes the materializer's own [SurfaceSampler] and [VegetationScatter] rather than building a second
     * pair from the same layers, for the reason `SurfaceSampler.of` gives about having one classifier: two
     * copies agree until one of them gains an input.
     */
    fun of(generated: GeneratedWorld) = SurfacePatchSampler(
      config = generated.config,
      base = generated.base,
      features = generated.world.features,
      surface = generated.materializer.surface,
      vegetation = generated.vegetation
    )
  }
}
