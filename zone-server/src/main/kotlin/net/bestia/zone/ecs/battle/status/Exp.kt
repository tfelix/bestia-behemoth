package net.bestia.zone.ecs.battle.status

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.SyncContext
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.message.EntitySMSG

data class Exp(
  private var _value: Int = 0
) : Component, Dirtyable {

  var value: Int
    get() = _value
    set(newValue) {
      if (_value != newValue) {
        _value = newValue
        dirty = true
      }
    }

  private var dirty = true

  override fun isDirty(): Boolean = dirty

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long): EntitySMSG {
    return ExpSMSG(entityId = entityId, exp = value)
  }

  override fun syncTargets(context: SyncContext, entityId: EntityId): SyncTargets {
    val owner = context.world.get(entityId, Account::class)?.accountId
    return SyncTargets.Accounts(setOfNotNull(owner))
  }
}
