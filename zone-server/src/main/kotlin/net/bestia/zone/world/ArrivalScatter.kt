package net.bestia.zone.world

import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureEvaluator
import net.bestia.worldgen.voxel.PondWaterSampler
import net.bestia.worldgen.voxel.RiverWaterSampler
import net.bestia.zone.geometry.Vec3L
import kotlin.random.Random

/**
 * Picks where a new master first stands: a random dry tile in the square around its ward stone, never the
 * stone's own tile.
 *
 * Reads the generated terrain rather than the live world because a master is created off the tick thread, so
 * player edits and props are not seen. Dry means the ground, with every feature stamped on it, is not under the
 * highest of the sea or lake, river and pond surfaces - the same contest `ChunkMaterializer` settles per column.
 */
object ArrivalScatter {

  /** Tiles either side of the stone, so the square is 15 x 15. */
  const val RADIUS_TILES = 7

  private const val MAX_ATTEMPTS = 32

  /** The stone itself when no dry tile turns up in [MAX_ATTEMPTS] tries. */
  fun pick(generated: GeneratedWorld, stone: Vec3L, random: Random): Vec3L {
    val config = generated.config
    val voxelSize = config.voxelSize
    val reach = (RADIUS_TILES + 1) * voxelSize
    val centreX = (stone.x + 0.5) * voxelSize
    val centreY = (stone.y + 0.5) * voxelSize

    val terrain = Terrain(generated, Aabb(centreX - reach, centreY - reach, centreX + reach, centreY + reach))

    repeat(MAX_ATTEMPTS) {
      val dx = random.nextInt(-RADIUS_TILES, RADIUS_TILES + 1)
      val dy = random.nextInt(-RADIUS_TILES, RADIUS_TILES + 1)
      if (dx == 0 && dy == 0) {
        return@repeat
      }

      val ground = terrain.dryGroundAt(centreX + dx * voxelSize, centreY + dy * voxelSize)
      if (ground != null) {
        return Vec3L(stone.x + dx, stone.y + dy, config.voxelZOf(ground).toLong())
      }
    }

    return stone
  }

  /** The features near the stone, queried once for every tile tried. Not thread safe, so built per call. */
  private class Terrain(private val generated: GeneratedWorld, area: Aabb) {
    private val config = generated.config
    private val features = generated.world.features.query(area)
    private val evaluator = FeatureEvaluator(features)
    private val rivers = RiverWaterSampler(features, config.voxelSize)
    private val ponds = PondWaterSampler(features)

    /** The ground height when the column is on the map and not under water, otherwise null. */
    fun dryGroundAt(worldX: Double, worldY: Double): Double? {
      if (worldX < 0.0 || worldX >= config.widthMetres || worldY < 0.0 || worldY >= config.heightMetres) {
        return null
      }

      val base = generated.base.heightAt(worldX, worldY)
      val ground = evaluator.heightAt(worldX, worldY, base)

      // The river and pond samplers answer NaN where they cover nothing, so those fall back to the standing level.
      val standing = generated.materializer.surface.waterLevelAt(worldX, worldY)
      val river = rivers.surfaceAt(worldX, worldY)
      val pond = ponds.surfaceAt(worldX, worldY)
      val water = maxOf(standing, if (river.isNaN()) standing else river, if (pond.isNaN()) standing else pond)

      if (water > ground) {
        return null
      }

      return ground
    }
  }
}
