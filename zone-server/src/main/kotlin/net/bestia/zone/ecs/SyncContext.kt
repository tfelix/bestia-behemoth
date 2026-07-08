package net.bestia.zone.ecs

import net.bestia.zone.ecs.core.World

// TODO Remove this shit again. A party membership lookup must be done via a component read. You can not just inject
//  stuff into this context. Way to inflexible.
interface PartyMembershipLookup {
  /** AccountIds of every other member of [accountId]'s party (empty if not partied). */
  fun partyMemberAccountIds(accountId: Long): Set<Long>
}

/** Passed to [Dirtyable.syncTargets] so components can resolve their audience against live state. */
class SyncContext(
  val world: World,
  private val partyLookup: PartyMembershipLookup,
) {
  private val partyCache = HashMap<Long, Set<Long>>()

  /** Memoized per flush so components sharing an owner don't repeat the party lookup. */
  fun partyMembersOf(accountId: Long): Set<Long> =
    partyCache.getOrPut(accountId) { partyLookup.partyMemberAccountIds(accountId) }
}
