package net.bestia.zone.honor.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.ecs.core.World
import net.bestia.zone.honor.HonorLedger
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.sync.Dirtyable
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.util.EntityId

/**
 * A Master's global honor. [shownValue] is what the owner sees: the ledger heals over days, so it is worked out
 * when the honor changes rather than on every sync.
 */
class Honor(
  ledger: HonorLedger = HonorLedger(),
  shownValue: Int = 0,
) : Component, Dirtyable {
  override val dirtyFlag = DirtyFlag()

  var ledger: HonorLedger = ledger
    private set

  var shownValue: Int = shownValue
    private set

  fun update(ledger: HonorLedger, shownValue: Int) {
    this.ledger = ledger
    if (this.shownValue != shownValue) {
      this.shownValue = shownValue
      markDirty()
    }
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return HonorComponentSMSG(entityId = entityId, globalHonor = shownValue)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.OwnerOnly
  }
}
