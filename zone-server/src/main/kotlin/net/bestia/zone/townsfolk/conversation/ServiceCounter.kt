package net.bestia.zone.townsfolk.conversation

import net.bestia.zone.util.AccountId

/**
 * Somebody at work who serves at a counter rather than chats, like a postmaster. Implemented by the slices that
 * own the counter, which sit above the townsfolk.
 */
interface ServiceCounter {

  /** @return true when [speaker] serves [accountId] here, so no conversation opens */
  fun serve(accountId: AccountId, speaker: Speaker): Boolean
}
