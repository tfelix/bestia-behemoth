package net.bestia.zone.world.stream

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.local.LocalWalkQuery
import org.springframework.stereotype.Service

/**
 * Answers [LocalWalkQuery] from the derived walkability tiles.
 *
 * Thin for the reason [ChunkGroundHeight] is thin: everything the streaming layer knows about chunk
 * coordinates, voxel sizes and the position-unit convention stays on this side of the seam.
 *
 * Unlike [ChunkGroundHeight] this reads `ChunkService.derived()` rather than the base heightfield, and that
 * is the whole point of it existing: the derived tiles are built from *merged* voxels, so a doorway a player
 * bricked up is not walkable and a stair they built is. Movement that consulted only the heightfield would
 * path NPCs through both.
 */
@Service
class ChunkWalkQuery(private val chunkService: ChunkService) : LocalWalkQuery {

  override fun canStep(from: Vec3L, to: Vec3L): Boolean {
    if (!chunkService.isReady) return false

    val config = chunkService.config
    val origin = ChunkCoords.localise(config, from.x, from.y, 0) ?: return false
    val target = ChunkCoords.localise(config, to.x, to.y, 0) ?: return false

    val originChunk = chunkService.normalise(origin.chunk)
    val targetChunk = chunkService.normalise(target.chunk)

    val derived = chunkService.derived()

    // Residency first, both ends. Without this a step into unloaded country materialises a chunk, and a
    // search that drifts off the edge of what players are looking at would do that per column.
    if (!derived.isTracked(originChunk) || !derived.isTracked(targetChunk)) return false

    // The surface being stepped off has to come from the tile rather than from the caller's own z: an
    // entity's position is rounded to a standing height, and `stepTarget` compares against the true
    // fractional surface. Feeding it a rounded value makes a legal step over a shallow slope look like a
    // rise of most of a voxel.
    val walkable = derived.walkableOf(originChunk)
    if (walkable.spanCountAt(origin.localX, origin.localY) == 0) return false
    val fromSurface = nearestSurface(walkable, origin.localX, origin.localY, from.z) ?: return false

    return derived.canStep(
      from = originChunk,
      fromLocalX = origin.localX,
      fromLocalY = origin.localY,
      fromSurface = fromSurface,
      to = targetChunk,
      toLocalX = target.localX,
      toLocalY = target.localY
    )
  }

  override fun surfaceAt(position: Vec3L): Long? {
    if (!chunkService.isReady) return null

    val config = chunkService.config
    val localised = ChunkCoords.localise(config, position.x, position.y, 0) ?: return null
    val chunk = chunkService.normalise(localised.chunk)

    val derived = chunkService.derived()
    if (!derived.isTracked(chunk)) return null

    val walkable = derived.walkableOf(chunk)
    if (walkable.spanCountAt(localised.localX, localised.localY) == 0) return null

    val surface = nearestSurface(walkable, localised.localX, localised.localY, position.z) ?: return null
    // surface is a fractional voxel height; multiplying gives its elevation, and standingZ rounds it.
    return ChunkCoords.standingZ(config, surface * config.voxelSize)
  }

  override fun isResident(position: Vec3L): Boolean {
    if (!chunkService.isReady) return false

    val localised = ChunkCoords.localise(chunkService.config, position.x, position.y, 0) ?: return false
    return chunkService.derived().isTracked(chunkService.normalise(localised.chunk))
  }

  /**
   * The standable surface in a column closest to where the entity thinks it is.
   *
   * A column can have several - one per floor of a building, one per chamber of a cave - and the one that
   * matters is the one under the entity's feet. Picking the lowest would walk anything on an upper floor
   * through it.
   */
  private fun nearestSurface(
    walkable: net.bestia.worldgen.derived.WalkableTile,
    localX: Int,
    localY: Int,
    z: Long
  ): Double? {
    val surfaces = walkable.surfacesAt(localX, localY)
    if (surfaces.isEmpty()) return null

    val target = z.toDouble()
    var best = surfaces[0]
    var bestGap = Math.abs(best - target)
    for (i in 1 until surfaces.size) {
      val gap = Math.abs(surfaces[i] - target)
      if (gap < bestGap) {
        bestGap = gap
        best = surfaces[i]
      }
    }
    return best
  }
}
