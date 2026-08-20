package net.bestia.zone.world.stream

import net.bestia.zone.ecs.movement.GroundHeight
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.local.LocalWalkQuery
import org.springframework.stereotype.Service

/**
 * Answers [GroundHeight] from the derived, edit-aware walkability tiles.
 *
 * Deliberately thin: the whole point of the interface is that `ecs/movement` does not depend on the streaming
 * layer, so all the knowledge about voxel sizes, sea level and the position-unit convention stays on this side
 * of it - here, entirely inside [LocalWalkQuery]'s own implementation.
 *
 * Delegates to [LocalWalkQuery.surfaceAt] rather than the generated heightfield, and that is the whole point of
 * this class existing rather than being deleted in favour of the interface it wraps: [ChunkWalkQuery] reads
 * *merged* voxels, so a hole a player carved reports its new floor, not the elevation the world generator gave
 * that column before anyone dug into it.
 */
@Service
class ChunkGroundHeight(private val walkQuery: LocalWalkQuery) : GroundHeight {

  override fun standingZAt(position: Vec3L): Long? = walkQuery.surfaceAt(position)
}
