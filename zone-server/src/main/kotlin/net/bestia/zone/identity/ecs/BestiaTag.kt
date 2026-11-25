package net.bestia.zone.identity.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.sync.Dirtyable
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.util.EntityId

/**
 * A player bestia's name tag as everyone in range reads it: the name its owner gave it, or null for its species'
 * name, and its owner's name. Replaced on a rename; a bestia that changes hands is spawned anew and gets its new
 * owner's.
 */
data class BestiaTag(
  val name: String?,
  val ownerName: String,
) : Component, Dirtyable {
  override val dirtyFlag = DirtyFlag()

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return BestiaTagComponentSMSG(entityId, name, ownerName)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}
