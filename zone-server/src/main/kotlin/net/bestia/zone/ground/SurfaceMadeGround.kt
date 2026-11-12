package net.bestia.zone.ground

import net.bestia.worldgen.voxel.BlockType
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.structure.StructureCollision
import org.springframework.stereotype.Service

/** Made ground is what the world was paved or trodden with, or a floor a player built. */
@Service
class SurfaceMadeGround(
  private val surface: SurfaceBlockLookup,
  private val structures: StructureCollision,
) : MadeGround {

  override fun isMadeAt(position: Vec3L): Boolean? {
    if (structures.standsOnPiece(position)) return true

    val block = surface.blockAt(position.x, position.y) ?: return null

    return block in MADE_SURFACES
  }

  companion object {
    /** The tread of a road, a town's cobbles, and the masonry of a bridge deck. */
    val MADE_SURFACES = setOf(BlockType.PACKED_EARTH, BlockType.COBBLESTONE, BlockType.MASONRY)
  }
}
