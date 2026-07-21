package net.bestia.zone.ecs.battle.status

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
) : CurMax(current, max), Component, Dirtyable {

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
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