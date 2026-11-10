package net.bestia.zone.casting.detect

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.sync.DirtyableComponent
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.util.EntityId

/**
 * What a Detect skill lets its bearer sense: masters and player bestia nearby, hidden ones too. [DetectingSystem]
 * looks again every second and takes it off when [remainingSeconds] run out. Only the bearer's player sees it.
 */
class Detecting(
  var remainingSeconds: Float,
  /** The Potency of the detecting cast, contested against a concealment's; see [DetectingSystem]. */
  var potency: Int = Int.MAX_VALUE,
) : DirtyableComponent() {

  var found: List<Detected> = emptyList()
    private set

  data class Detected(val entityId: EntityId, val position: Vec3L, val isMaster: Boolean)

  fun update(next: List<Detected>) {
    if (next != found) {
      found = next
      markDirty()
    }
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return DetectingComponentSMSG(entityId, found, removed)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.OwnerOnly
  }
}
