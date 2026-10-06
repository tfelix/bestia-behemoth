package net.bestia.zone.session

import net.bestia.zone.BestiaEvent

class AccountDisconnectedEvent(source: Any, val accountId: Long) : BestiaEvent(source) {

  /** The order its listeners run in, pinned: Spring would otherwise follow their package paths. */
  object ListenerOrder {
    const val ENTITY_CONTROL = 10
    const val HTTP_TICKETS = 20
    const val MOVE_RATE_LIMIT = 30
    const val TRADE = 40
    const val WORLD_INFO = 50
  }
}