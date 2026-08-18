package net.bestia.zone.cartography.render

import net.bestia.worldgen.core.BaseHeightField
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.VectorFeature

/**
 * Everything a [MapStyle] is allowed to read, resolved once per world instead of per tile.
 *
 * The layer lookups are the reason this exists. `LayerStore.require` is a map read plus a cast, and a
 * style asks for the same six layers on every one of a few hundred thousand tiles; worse, a typo in a
 * [LayerId] would surface as a failed tile rather than a failed boot. Resolving them here means a missing
 * layer fails once, loudly, at construction.
 *
 * Deliberately **not** carrying the chunk tier. `ChunkService` is documented single-threaded on the zone
 * tick, and tiles render on their own pool; the two things a style may sample off-thread are the rasters
 * (plain array reads) and [GeneratedWorld.base], whose sampler holds no mutable state.
 */
class TileInputs(
  val config: WorldConfig,
  val elevation: FloatLayer,
  val waterLevel: FloatLayer,
  val biome: IntLayer,
  val canopyCover: FloatLayer,
  val iceThickness: FloatLayer,
  val discharge: FloatLayer,
  val chronicle: Chronicle,

  /**
   * The continuous heightfield, for relief texture at close zoom only.
   *
   * Never for the land-water boundary: that comes off [elevation] at every level, so a coastline traced at
   * one zoom lands on the coastline traced at another. This carries metre-scale detail the kilometre raster
   * cannot, which is worth having at 16 m per pixel and is pure noise at 512, where a single sample per
   * pixel of a metre-scale field aliases into speckle. See [DetailRelief.MAX_METRES_PER_PIXEL].
   */
  val baseHeight: BaseHeightField,

  /**
   * Features overlapping a query box, in `(priority, id)` order.
   *
   * A function rather than a list because a world holds tens of thousands of features and a tile wants
   * the handful that reach it. Backed by the frozen `FeatureStore` index, so this is safe to call
   * concurrently.
   */
  val featuresIn: (Aabb) -> List<VectorFeature>,

  /** Draw place names into the tile. Off for served tiles, which leave labels to the client. */
  val labels: Boolean = false
) {

  val seed: Long get() = config.seed
  val seaLevel: Double get() = config.seaLevel

  companion object {

    fun of(generated: GeneratedWorld, labels: Boolean = false): TileInputs {
      val layers = generated.world.layers

      return TileInputs(
        config = generated.config,
        elevation = layers.require(LayerId.ELEVATION),
        waterLevel = layers.require(LayerId.WATER_LEVEL),
        biome = layers.require(LayerId.BIOME),
        canopyCover = layers.require(LayerId.CANOPY_COVER),
        iceThickness = layers.require(LayerId.ICE_THICKNESS),
        discharge = layers.require(LayerId.DISCHARGE),
        chronicle = generated.world.chronicle,
        baseHeight = generated.base,
        featuresIn = { generated.world.features.query(it) },
        labels = labels
      )
    }
  }
}
