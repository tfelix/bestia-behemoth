package net.bestia.worldgen.core

import net.bestia.worldgen.vector.FeatureEvaluator

/**
 * The base heightfield, before any vector feature is stamped: raster sample plus whatever detail
 * the chunk tier adds.
 *
 * It must be a pure function of world position. Vector features guarantee their own continuity, but
 * they blend against this - if this has seams, the river floor is continuous and its banks are not.
 * Any chunk-local detail erosion therefore has to run with an overlap margin and blend in the
 * overlap; that margin exists for the raster tier only.
 */
fun interface BaseHeightField {
  fun heightAt(worldX: Double, worldY: Double): Double
}

/**
 * Column heights for one chunk, optionally expanded by a halo of columns from the neighbours.
 *
 * The halo is what the chunk-boundary stress view compares: a chunk's halo column and its
 * neighbour's first interior column are the same world column, so they must agree exactly.
 */
class ColumnHeights private constructor(
  val chunk: ChunkPos,
  val size: Int,
  val halo: Int,
  private val data: DoubleArray
) {

  val stride = size + 2 * halo

  /** @param localX,localY in `-halo until size + halo` */
  operator fun get(localX: Int, localY: Int): Double {
    require(localX >= -halo && localX < size + halo && localY >= -halo && localY < size + halo) {
      "($localX,$localY) is outside chunk $chunk with halo $halo"
    }
    return data[(localY + halo) * stride + (localX + halo)]
  }

  /** World column index of a local column, so two chunks can be compared without index arithmetic. */
  fun globalColumn(localX: Int, localY: Int): Pair<Long, Long> =
    (chunk.x.toLong() * size + localX) to (chunk.y.toLong() * size + localY)

  companion object {
    fun build(
      chunk: ChunkPos,
      size: Int,
      halo: Int,
      height: (localX: Int, localY: Int) -> Double
    ): ColumnHeights {
      require(halo >= 0) { "halo must not be negative, was $halo" }

      val stride = size + 2 * halo
      val data = DoubleArray(stride * stride)
      for (localY in -halo until size + halo) {
        for (localX in -halo until size + halo) {
          data[(localY + halo) * stride + (localX + halo)] = height(localX, localY)
        }
      }

      return ColumnHeights(chunk, size, halo, data)
    }
  }
}

/**
 * Anything that can produce the column heights of a chunk.
 *
 * [ChunkSeamCheck] takes this rather than a concrete sampler, so the same harness can be pointed at
 * the real chunk pipeline once detail erosion and materialisation exist on top of it - and so a test
 * can point it at a deliberately broken source to prove the harness still catches seams.
 */
fun interface ChunkColumnSource {
  fun heights(chunk: ChunkPos, halo: Int): ColumnHeights
}

/**
 * Produces the terrain height of every voxel column in a chunk: base heightfield, then every vector
 * feature whose corridor reaches the chunk, in `(priority, id)` order.
 *
 * This is steps 1 and 2 of `generate_chunk` in worldgen-architecture.md. Materialisation - strata,
 * caves, water, ore, scatter - happens above it, and everything chunk-seeded happens after, never
 * here, because nothing chunk-seeded may influence geometry that crosses a chunk border.
 *
 * Safe to share across threads: it holds no mutable state, and the per-column scratch lives in the
 * [FeatureEvaluator] built for each call.
 */
class ChunkHeightSampler(
  private val config: WorldConfig,
  private val base: BaseHeightField,
  private val features: FeatureStore
) : ChunkColumnSource {

  override fun heights(chunk: ChunkPos, halo: Int): ColumnHeights {
    // The index stores bounds already expanded by each feature's influence radius, so intersecting
    // the chunk's own bounds is enough - a miss genuinely cannot reach any column here.
    val margin = halo * config.voxelSize
    val evaluator = FeatureEvaluator(features.query(config.chunkBounds(chunk).expanded(margin)))

    return ColumnHeights.build(chunk, config.chunkSize, halo) { localX, localY ->
      val (worldX, worldY) = config.columnCenter(chunk, localX, localY)
      val baseHeight = base.heightAt(worldX, worldY)
      if (evaluator.isEmpty) baseHeight else evaluator.heightAt(worldX, worldY, baseHeight)
    }
  }

  fun heights(chunk: ChunkPos) = heights(chunk, halo = 0)
}
