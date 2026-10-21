package net.bestia.zone.identity.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.AccountId

/**
 * Marks a master entity as being part of a party, caching the roster and its EXP mode so the tick can address
 * party-wide state and split kill EXP with a plain component read instead of a database lookup.
 *
 * Kept up to date by [net.bestia.zone.party.PartyService] on every join, leave, kick, disband and mode change,
 * once it has committed, and added by `MasterEntitySpawner` when a member logs in.
 *
 * Keyed by master as well as account, because an account can play another of its masters: only a member whose
 * account plays *that* master counts (see `ConnectionInfoService.isPlaying`).
 */
data class PartyMembership(
  val partyId: Long,
  val members: Set<Member>,
  val expShare: PartyExpShare,
) : Component {

  data class Member(
    val accountId: AccountId,
    val masterId: Long,
  )
}
