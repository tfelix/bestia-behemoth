package net.bestia.zone.trade

import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId

/**
 * One trade between two players, from the moment it is asked for until it closes.
 *
 * Lives only in memory. [TradeService] holds the map and is the only thing that mutates a session, always
 * inside `synchronized(session)` - message handlers run on socket threads and the range sweep runs on the
 * tick thread, so every transition has to be one indivisible step. What is durable is the reservation each
 * offered item carries on its own container row, which is deliberately the *only* thing that survives a
 * restart: see [net.bestia.zone.boot.TradeReservationCleanupBootRunner].
 */
internal class TradeSession(
  val tradeId: Long,
  val requester: Side,
  val target: Side,
) {

  var status: TradeStatus = TradeStatus.PENDING

  class Side(
    val accountId: AccountId,
    val masterId: Long,
    val entityId: EntityId,
    val masterName: String,
  ) {
    /** What this side has put in, in the order they put it in. */
    val offer: MutableList<ReservedItem> = mutableListOf()

    var locked: Boolean = false
    var confirmed: Boolean = false
  }

  val bothLocked: Boolean get() = requester.locked && target.locked
  val bothConfirmed: Boolean get() = requester.confirmed && target.confirmed

  fun sideOf(accountId: AccountId): Side? = when (accountId) {
    requester.accountId -> requester
    target.accountId -> target
    else -> null
  }

  /** The other half of the trade. Non-null because a [Side] can only have come from this session. */
  fun partnerOf(side: Side): Side = if (side === requester) target else requester

  fun sides(): List<Side> = listOf(requester, target)

  /**
   * Drops every readiness flag, which any change to either offer does.
   *
   * This is what makes "both locked" mean "both have looked at exactly these contents", and it is the whole
   * anti-scam rule: nobody can slip an item out from under a partner who has already said yes.
   */
  fun clearReadiness() {
    sides().forEach {
      it.locked = false
      it.confirmed = false
    }
  }
}
