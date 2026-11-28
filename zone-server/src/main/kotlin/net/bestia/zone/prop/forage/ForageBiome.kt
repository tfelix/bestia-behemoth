package net.bestia.zone.prop.forage

import net.bestia.worldgen.bio.Biome
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/**
 * The biome a forage patch grows in. An interface for `ForageGround`'s reason: the real answer needs a generated
 * world, and a test of what a pick yields has none.
 */
fun interface ForageBiome {

  /** The biome at [position], in tiles; null when there is no world to ask. */
  fun biomeAt(position: Vec3L): Biome?
}

/** The biome the world generator put at that tile. */
@Service
class GeneratedForageBiome(private val worldService: WorldService) : ForageBiome {

  override fun biomeAt(position: Vec3L): Biome? {
    if (!worldService.isLoaded) return null
    val generated = worldService.generated
    val metresPerTile = generated.config.voxelSize

    return generated.materializer.surface.biomeAt(position.x * metresPerTile, position.y * metresPerTile)
  }
}
