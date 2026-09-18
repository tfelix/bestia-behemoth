package net.bestia.zone.world.ground

import net.bestia.worldgen.voxel.BlockType
import org.springframework.stereotype.Service

/**
 * Whether ground wears bare, as a property of what is standing on top of it.
 *
 * ### Anything absent is zero
 *
 * [CAP_WEAR] is the whole rule, so rock, gravel, ice, every ore, water, and the cobblestone and masonry a road
 * or a bridge is made of are unwearable **by construction** rather than by an exclusion list somebody has to
 * remember to extend. A paved road not turning into a dirt track is the case that matters: it is already the
 * path, and wearing it would be drawing a desire line along the thing people desired.
 *
 * ### Sand and snow are deliberately not in [CAP_WEAR]
 *
 * They take a *print* rather than wearing *bare* - there is no grass on them to kill and no earth under them
 * to expose, and a trail across a dune is gone with the next wind. So they are in [CAP_IMPRESSION] instead,
 * where they are the strongest entries. Putting them in the first map would give a beach permanent brown
 * paths, which is the wrong answer arrived at by the right mechanism.
 */
@Service
class SurfaceTrampleableGround(
  private val surface: SurfaceBlockLookup,
) : TrampleableGround {

  override fun wearAt(voxelX: Long, voxelY: Long): Double {
    return surface.blockAt(voxelX, voxelY)?.let { CAP_WEAR[it] } ?: 0.0
  }

  override fun impressionAt(voxelX: Long, voxelY: Long): Double {
    return surface.blockAt(voxelX, voxelY)?.let { CAP_IMPRESSION[it] } ?: 0.0
  }

  private companion object {

    /**
     * The only surface blocks that wear bare, and how readily.
     *
     * A map rather than a set, because living grass gives way faster than what is left of it in a drought -
     * the same distinction `CAP_FUEL` already draws for fire, read the other way round.
     */
    val CAP_WEAR = mapOf(
      BlockType.GRASS to 1.0,
      BlockType.DRY_GRASS to 0.8,
      // Dead matter over corrupted soil: it breaks down readily, and a track through blight staying visible
      // longer than one through meadow is the interesting answer rather than the safe one.
      BlockType.BLIGHTED_GRASS to 0.9,
      // Already bare. It still compacts, which is what turns a used route from mud into a hard track.
      BlockType.MUD to 0.6,
      BlockType.DIRT to 0.4,
    )

    /**
     * The only surface blocks a footfall leaves a mark in, and how deep.
     *
     * Absent is zero again, so stone, ice, ore and paving hold no tracks by construction.
     *
     * The ordering is what makes tracking a skill worth having somewhere rather than everywhere: snow and mud
     * record a crossing that happened hours ago, a meadow shows the grass bent for a while, and a hard track
     * shows almost nothing - which is the same reason a road is the sensible way to travel unseen.
     */
    val CAP_IMPRESSION = mapOf(
      BlockType.SNOW to 1.0,
      BlockType.MUD to 1.0,
      BlockType.SAND to 0.8,
      BlockType.BLIGHTED_SAND to 0.8,
      BlockType.DIRT to 0.5,
      BlockType.BLIGHTED_DIRT to 0.5,
      BlockType.GRAVEL to 0.3,
      BlockType.GRASS to 0.35,
      BlockType.DRY_GRASS to 0.3,
      BlockType.BLIGHTED_GRASS to 0.3,
    )
  }
}
