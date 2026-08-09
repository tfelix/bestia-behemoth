package net.bestia.zone.ai.perception

import net.bestia.worldgen.bio.Biome
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/**
 * Whether a grazing animal standing somewhere would find anything to eat there — the world lookup behind
 * [ForageSense], kept separate from the sense itself.
 *
 * An interface with one real implementation, for one reason worth the indirection: the real answer needs a
 * generated world, and an AI scenario test has none. Faking a biome raster to ask "does a hungry deer walk to
 * the grass" is a great deal of machinery for a question with a boolean answer.
 */
fun interface ForageGround {

  /** Whether [position] is ground a herbivore can feed on. */
  fun isGrazeable(position: Vec3L): Boolean
}

/**
 * The real answer: the biome the world generator put at that tile.
 *
 * There is deliberately no vegetation *entity* behind this and no per-tile store. Forage is modelled as a
 * property of the ground, which is what the biome raster already is — so grass is wherever grass grows, for
 * free, on a world of any size, with nothing to spawn, persist or clean up. What a creature remembers about
 * where it fed is a separate thing and lives on its blackboard (see [ForageSense]).
 *
 * [GRAZEABLE] is a list rather than a threshold on `Biome.litter`, tempting though the latter is. Litter is
 * how much dead matter a biome puts into its soil, and bog scores 0.85 on it precisely *because* nothing
 * there decays — peat is the absence of grazing, not evidence of it. A short reviewable list says what is
 * meant; a threshold would have quietly sent every herbivore into the mires.
 */
@Service
class BiomeForageGround(private val worldService: WorldService) : ForageGround {

  override fun isGrazeable(position: Vec3L): Boolean {
    val generated = worldService.generated
    val metresPerTile = generated.config.voxelSize

    val biome = generated.materializer.surface.biomeAt(
      position.x * metresPerTile,
      position.y * metresPerTile,
    )

    return biome in GRAZEABLE
  }

  companion object {
    /** Biomes with something growing at ground level that a herbivore would eat. */
    private val GRAZEABLE = setOf(
      Biome.GRASSLAND,
      Biome.DRYLAND,
      Biome.TUNDRA,
      Biome.RIPARIAN,
      Biome.TAIGA,
      Biome.TEMPERATE_FOREST,
      Biome.TEMPERATE_RAINFOREST,
      Biome.TROPICAL_SEASONAL_FOREST,
      Biome.TROPICAL_RAINFOREST,
      Biome.SWAMP,
    )
  }
}
