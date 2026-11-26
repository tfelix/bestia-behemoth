package net.bestia.zone.item.ecs

import net.bestia.zone.util.AccountId

/** Only [accounts] may pick a drop up, for [seconds]. */
data class LootClaim(
  val accounts: Set<AccountId>,
  val seconds: Float,
)
