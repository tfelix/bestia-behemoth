package net.bestia.zone.casting.portal

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.sync.DirtyableComponent
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.util.EntityId

/**
 * The spots a Sage saved for Warp Portal, oldest first. Kept on the master's row (`warp_marks`) and shown only to
 * the owner, whose cast picker lists them.
 */
class WarpMarks(spots: List<Vec3L> = emptyList()) : DirtyableComponent() {

  var spots: List<Vec3L> = spots
    private set

  /** Saves [at]; once [capacity] spots are kept, the oldest makes room. */
  fun mark(at: Vec3L, capacity: Int) {
    spots = (spots + at).takeLast(capacity.coerceAtLeast(1))
    markDirty()
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return WarpMarksComponentSMSG(entityId, spots)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.OwnerOnly

  companion object {
    /** How the spots sit in their column: `x:y:z` joined by `;`. */
    fun encode(spots: List<Vec3L>): String = spots.joinToString(";") { "${it.x}:${it.y}:${it.z}" }

    fun decode(column: String): List<Vec3L> {
      return column.split(';').filter { it.isNotBlank() }.mapNotNull { spot ->
        val parts = spot.split(':').mapNotNull { it.toLongOrNull() }
        if (parts.size == 3) Vec3L(parts[0], parts[1], parts[2]) else null
      }
    }
  }
}
