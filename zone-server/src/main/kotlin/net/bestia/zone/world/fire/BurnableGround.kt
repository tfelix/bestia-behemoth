package net.bestia.zone.world.fire

import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.SurfaceCover
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkService
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
 * ### The bog trap closes itself
 *
 * `BiomeForageGround`'s KDoc warns that a threshold on litter "would have quietly sent every herbivore into
 * the mires", bog scoring 0.85 on it. That cannot happen here and not because of a hand-written exclusion
 * list: bog caps in `MUD`, `MUD` is not a burnable cap, so fuel is zero before litter is ever read. The cap
 * test does the work a list would have had to remember to do.
 *
 * ### What this does not know about: roads
 *
 * Streets and roads are stamped `COBBLESTONE` into the *voxels* by `TownStructures`, which is a decision
 * `SurfaceCover.cap` never sees - it answers from the biome and the climate. So **a fire crosses a paved road
 * today.** Stated rather than left to be discovered; closing it wants one bounded feature query per fire at
 * ignition, not a per-cell lookup here.
 */
@Service
class SurfaceBurnableGround(
  private val worldService: WorldService,
  /**
   * For the ground height, and only for that.
   *
   * Needed because the water test cannot come from the biome: a river running through grassland is a *feature*
   * stamped into a RIPARIAN or GRASSLAND cell, so the biome says "things grow here" and the water says
   * otherwise. `VegetationScatter.propsIn` makes the same call in the same words - "a biome is a kilometre cell
   * and a pond edge is not, so the water surface has the last word".
   */
  private val chunkService: ChunkService,
) : BurnableGround {

  override fun fuelAt(voxelX: Long, voxelY: Long): Double {
    if (!worldService.isLoaded) return 0.0

    val config = worldService.config
    val worldX = voxelX * config.voxelSize
    val worldY = voxelY * config.voxelSize

    val surface = worldService.generated.materializer.surface

    // Standing water, from the surface rather than from the biome - see the constructor note. A null ground
    // is a column outside the world, which is not burning either.
    val ground = chunkService.surfaceElevationAt(voxelX, voxelY) ?: return 0.0
    if (surface.waterLevelAt(worldX, worldY) > ground) return 0.0

    val biome = surface.biomeAt(worldX, worldY)
    val cap = SurfaceCover.cap(
      biome,
      surface.temperatureAt(worldX, worldY),
      0.0,
      surface.isBlightedAt(worldX, worldY)
    )

    val capFuel = CAP_FUEL[cap] ?: return 0.0

    // Litter carries the fire and canopy shades the ground that would otherwise dry out, so an open grassland
    // burns better than a closed forest floor at the same litter. Shaped rather than balanced.
    val openness = 1.0 - biome.canopy * CANOPY_DAMPING
    return (biome.litter * LITTER_WEIGHT * openness * capFuel).coerceIn(0.0, 1.0)
  }

  private companion object {

    /**
     * The only caps that carry a fire, and what each is worth.
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
