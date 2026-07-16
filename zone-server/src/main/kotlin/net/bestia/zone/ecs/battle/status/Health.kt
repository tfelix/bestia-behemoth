package net.bestia.zone.ecs.battle.status

import net.bestia.zone.ecs.battle.status.HealthComponentSMSG
import net.bestia.zone.battle.status.CurMax
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.party.PartyMembership

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

  override fun markDirty() {
    dirty = true
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

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    val owner = world.get(entityId, Account::class)?.accountId
      ?: return SyncTargets.PublicInRange // mobs have no owner: HP stays visible to everyone nearby
    val partyMemberAccountIds = world.get(entityId, PartyMembership::class)?.memberAccountIds ?: emptySet()
    return SyncTargets.Accounts(partyMemberAccountIds + owner)
  }
}