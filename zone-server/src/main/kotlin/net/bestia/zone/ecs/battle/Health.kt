package net.bestia.zone.ecs.battle

import net.bestia.zone.component.HealthComponentSMSG
import net.bestia.zone.battle.status.CurMax
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.SyncContext
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.player.Account
import net.bestia.zone.message.EntitySMSG

class Health(
  current: Int,
  max: Int
) : CurMax(), Component, Dirtyable {
  private var dirty = true

  override var current: Int
    get() = super.current
    set(value) {
      val oldValue = super.current
      super.current = value
      if (super.current != oldValue) {
        dirty = true
      }
    }

  override var max: Int
    get() = super.max
    set(value) {
      val oldValue = super.max
      super.max = value
      if (super.max != oldValue) {
        dirty = true
      }
    }

  init {
    this.max = max
    this.current = current
  }

  override fun isDirty(): Boolean {
    return dirty
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long): EntitySMSG {
    return HealthComponentSMSG(
      entityId = entityId,
      current = current,
      max = max
    )
  }

  override fun syncTargets(context: SyncContext, entityId: EntityId): SyncTargets {
    val owner = context.world.get(entityId, Account::class)?.accountId
      ?: return SyncTargets.PublicInRange // mobs have no owner: HP stays visible to everyone nearby
    return SyncTargets.Accounts(setOf(owner) + context.partyMembersOf(owner))
  }
}