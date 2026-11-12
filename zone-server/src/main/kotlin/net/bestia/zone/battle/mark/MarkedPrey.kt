package net.bestia.zone.battle.mark

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.sync.DirtyableComponent
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.util.EntityId

/**
 * Whom a hunter's mark names now and where it was last seen, for the hunter's map; null once it has run out. Kept on
 * the hunter, see `HuntersMarked`, and followed by [MarkedPreySystem]. Only the hunter's player is told.
 */
class MarkedPrey : DirtyableComponent() {

  var preyId: EntityId? = null
    set(value) {
      if (field != value) {
        field = value
        position = null
        markDirty()
      }
    }

  var position: Vec3L? = null
    private set

  fun follow(at: Vec3L) {
    if (position != at) {
      position = at
      markDirty()
    }
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return MarkedPreyComponentSMSG(entityId, preyId?.takeUnless { removed }, position)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.OwnerOnly
  }
}
