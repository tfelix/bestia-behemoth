package net.bestia.zone.world.stream

import net.bestia.zone.ecs.movement.GroundHeight
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.local.LocalWalkQuery
import org.springframework.stereotype.Service

/**
 * Answers [GroundHeight] from the derived, edit-aware walkability tiles, falling back to the heightfield.
 *
 * Deliberately thin: the whole point of the interface is that `ecs/movement` does not depend on the streaming
 * layer, so all the knowledge about voxel sizes, sea level and the position-unit convention stays on this side
 * of it - here, entirely inside [ChunkWalkQuery] and [ChunkCoords].
 *
 * ### Two sources, in that order, and the order is the whole design
 *
 * [LocalWalkQuery.surfaceAt] reads *merged* voxels, so a hole a player carved reports its new floor rather
 * than the elevation the generator gave that column before anyone dug into it. That is the answer worth
 * having, and it is the one asked for first.
 *
 * It is also only available for a chunk whose derived tile has already been built, which is paid for out of a
 * per-tick budget once the chunk enters somebody's subscription - so there is a window, right after a chunk
 * arrives, where the edit-aware answer does not exist yet. Returning `null` there is what made a fresh spawn
 * and a `/mm` teleport unanswerable, and `MoveSystem` fall through to whatever height the client claimed.
 * `ChunkService.surfaceElevationAt` closes that window from the heightfield instead: a raster sample plus a
 * feature query, always available, and **not edit-aware** - so a spawn over a carved hole reads the generated
 * surface until the tile lands. Slightly stale beats unknown, and only in that window.
 *
 * `null` now means what it says: the position is off the world.
 */
@Service
class ChunkGroundHeight(
  private val walkQuery: LocalWalkQuery,
  private val chunkService: ChunkService
) : GroundHeight {

  override fun standingZAt(position: Vec3L): Long? {
    walkQuery.surfaceAt(position)?.let { return it }

    if (!chunkService.isReady) return null
    val elevation = chunkService.surfaceElevationAt(position.x, position.y) ?: return null

    return ChunkCoords.standingZ(chunkService.config, elevation)
  }
}
