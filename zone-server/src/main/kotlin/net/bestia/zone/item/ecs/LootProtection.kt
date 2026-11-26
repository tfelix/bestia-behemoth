package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.sync.Removable
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId

/**
 * A drop only some accounts may pick up, for a while: kill loot for its killer's side, a hand-over for its
 * receiver. Each claim hands over to the next when it runs out, and the last one frees the drop for everyone.
 * Not persisted, so a restart frees it early. Everyone nearby is told the holders, so the client can show who
 * may pick it up.
 */
class LootProtection(claims: List<LootClaim>) : Component, Removable {

  override val dirtyFlag = DirtyFlag()

  constructor(ownerAccountId: AccountId, remainingSeconds: Float) : this(
    listOf(LootClaim(setOf(ownerAccountId), remainingSeconds))
  )

  private val pending = ArrayDeque(claims)

  init {
    require(pending.isNotEmpty()) { "A loot protection needs at least one claim" }
  }

  var remainingSeconds: Float = pending.first().seconds
    private set

  /** The accounts that may pick the drop up now. */
  val holders: Set<AccountId>
    get() {
      return pending.first().accounts
    }

  fun mayLoot(accountId: AccountId?): Boolean {
    return accountId in holders
  }

  /** @return false once the last claim has run out */
  fun countDown(seconds: Float): Boolean {
    remainingSeconds -= seconds
    while (remainingSeconds <= 0f) {
      // The last claim stays, so the holders can still be read until the component is gone.
      if (pending.size == 1) {
        return false
      }
      pending.removeFirst()
      remainingSeconds += pending.first().seconds
      markDirty()
    }

    return true
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return LootClaimComponentSMSG(entityId, if (removed) emptySet() else holders, removed)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.PublicInRange
  }
}
