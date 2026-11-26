package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.AccountId

/**
 * A drop only some accounts may pick up, for a while: kill loot for its killer's side, a hand-over for its
 * receiver. Each claim hands over to the next when it runs out, and the last one frees the drop for everyone.
 * Not persisted, so a restart frees it early.
 */
class LootProtection(claims: List<LootClaim>) : Component {

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
      pending.removeFirst()
      val next = pending.firstOrNull() ?: return false
      remainingSeconds += next.seconds
    }

    return true
  }
}
