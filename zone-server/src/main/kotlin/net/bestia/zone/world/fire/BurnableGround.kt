package net.bestia.zone.world.fire

import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.SurfaceColumns
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/**
 * How readily the ground at one tile carries a fire.
 *
 * An interface with one real implementation, for the reason `ForageGround`'s KDoc gives for the same shape: the
 * real answer needs a generated world, and a test asking "does a fire cross a river" should not have to build
 * one to find out.
 */
fun interface BurnableGround {

  /** `0` for ground that does not burn at all, up to `1` for tinder. */
  fun fuelAt(voxelX: Long, voxelY: Long): Double
}

/**
 * Fuel as a property of the ground, which is what the biome raster already is.
 *
 * Modelled on `BiomeForageGround` deliberately, including the part worth copying: *"There is deliberately no
 * vegetation entity behind this and no per-tile store."* Grass is wherever grass grows, for free, on a world
 * of any size, with nothing to spawn or clean up - and fuel is the same question with a different threshold.
 *
 * ### `Biome.litter` is used here and `VegetationScatter` refuses it, and both are right
 *
 * Litter is how much dead matter a biome puts into its soil, which is exactly what carries a grass fire. The
 * scatter refuses it as a proxy for *tree* cover because grassland is one of the best litter producers on the
 * list while being almost treeless - which is precisely why it belongs here.
 *
 * ### It asks the voxels what the ground is, not the climate
 *
 * The obvious source is `SurfaceCover.cap(biome, temperature, ...)`, which answers from the biome and the
 * climate - and that is what the first version used. It has a hole: streets are stamped `COBBLESTONE`,
 * bridges `MASONRY`, a mine collar worked stone, all by the *voxel* pass, and `cap` never sees any of it. A
 * fire crossed a paved road.
 *
 * `ChunkMaterializer.surfaceColumns` gives the block actually standing at the top of each column, so every
 * one of those is a firebreak for free and without a geometry test - a road is not burnable because a road is
 * cobblestone, which is the same reason a cliff is not. Cached per column, because it materialises up to two
 * slabs per call.
 *
 * ### Two traps that close themselves
 *
 * **Bog.** `BiomeForageGround`'s KDoc warns that a threshold on litter "would have quietly sent every
 * herbivore into the mires", bog scoring 0.85 on it. Bog's surface is `MUD`, which is not in [CAP_FUEL], so
 * fuel is zero before litter is read - no exclusion list to forget a biome from.
 *
 * **Water.** A river through grassland is a *feature* in a RIPARIAN cell, so the biome says things grow here.
 * The surface block over it is water, and water is not in [CAP_FUEL] either.
 */
@Service
class SurfaceBurnableGround(
  private val worldService: WorldService,
) : BurnableGround {

  /**
   * Chunk column -> the block at the top of each of its columns.
   *
   * **Never invalidated**, on `ChunkStreamConfig.slabCacheCapacity`'s argument: this is a pure function of the
   * generated world. A player carving terrain could in principle change it, and the consequence is a fire
   * treating a freshly-dug pit as whatever used to be on top - which is not worth an invalidation path for a
   * mechanic about grass.
   */
  private val surfaceCache = object : LinkedHashMap<Long, SurfaceColumns>(CACHE_CAPACITY, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<Long, SurfaceColumns>) = size > CACHE_CAPACITY
  }

  override fun fuelAt(voxelX: Long, voxelY: Long): Double {
    if (!worldService.isLoaded) return 0.0

    val config = worldService.config
    val chunkSize = config.chunkSize.toLong()

    val chunkX = Math.floorDiv(voxelX, chunkSize).toInt()
    val chunkY = Math.floorDiv(voxelY, chunkSize).toInt()

    val columns = surfaceCache.getOrPut(ScorchRegistry.columnKeyOf(chunkX, chunkY)) {
      worldService.generated.materializer.surfaceColumns(chunkX, chunkY)
    }

    val block = columns.blockAt(
      Math.floorMod(voxelX, chunkSize).toInt(),
      Math.floorMod(voxelY, chunkSize).toInt()
    )

    // Everything not in the table is zero - water, sand, snow, ice, mud, every rock, and the cobblestone and
    // masonry a road or a bridge is made of. Unburnable by construction rather than by an exclusion list.
    // `ofOrNull`, not `of`: the throwing variant means "written by another version", which is the right
    // reaction when decoding a stored chunk and the wrong one on the tick thread inside a grass fire.
    val blockFuel = BlockType.ofOrNull(block)?.let { CAP_FUEL[it] } ?: return 0.0

    val worldX = voxelX * config.voxelSize
    val worldY = voxelY * config.voxelSize
    val biome = worldService.generated.materializer.surface.biomeAt(worldX, worldY)

    // Litter carries the fire and canopy shades the ground that would otherwise dry out, so an open grassland
    // burns better than a closed forest floor at the same litter. Shaped rather than balanced.
    val openness = 1.0 - biome.canopy * CANOPY_DAMPING
    return (biome.litter * LITTER_WEIGHT * openness * blockFuel).coerceIn(0.0, 1.0)
  }

  private companion object {

    /** Chunk columns held at once. A fire spans a handful; a view volume is 121. */
    const val CACHE_CAPACITY = 512

    /**
     * The only surface blocks that carry a fire, and what each is worth.
     *
     * A map rather than a set plus a constant, because dry grass genuinely burns better than green: it is the
     * one distinction the cap table already draws that fire cares about. Anything absent is zero - so sand,
     * snow, ice, mud, gravel, every rock and every worked stone are unburnable by construction rather than by
     * an exclusion list that a new cap could be forgotten from.
     */
    val CAP_FUEL = mapOf(
      BlockType.DRY_GRASS to 1.0,
      BlockType.GRASS to 0.8,
      // Blighted ground is dead matter over corrupted soil. It burns, and a little more readily than living
      // grass, which is the interesting answer rather than the safe one.
      BlockType.BLIGHTED_GRASS to 0.9,
    )

    /** Gain on `Biome.litter`, the single free scalar - `VegetationParams.canopyGain`'s argument. */
    const val LITTER_WEIGHT = 1.2

    /** How much a closed canopy protects the ground under it. Never all of it: a forest floor does burn. */
    const val CANOPY_DAMPING = 0.5
  }
}
