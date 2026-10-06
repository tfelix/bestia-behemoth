package net.bestia.zone.battle.ecs.status

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.battle.status.CurMax
import net.bestia.zone.identity.ecs.PartyMembership

class Mana(
  current: Int,
  max: Int
) : CurMax(current, max), Component {

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return ManaComponentSMSG(
      entityId = entityId,
      current = current,
      max = max
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    val owner = world.get(entityId, Account::class)?.accountId
      ?: return SyncTargets.Accounts(emptySet())
    val partyMemberAccountIds = world.get(entityId, PartyMembership::class)?.memberAccountIds ?: emptySet()
    return SyncTargets.Accounts(partyMemberAccountIds + owner)
  }
}