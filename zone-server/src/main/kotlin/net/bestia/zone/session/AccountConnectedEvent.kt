package net.bestia.zone.session

import net.bestia.account.Authority
import net.bestia.zone.BestiaEvent

class AccountConnectedEvent(
  source: Any,
  val accountId: Long,
  val authorities: Set<Authority>,
) : BestiaEvent(source) {

  /** The order its listeners run in after provisioning (`HIGHEST_PRECEDENCE`), pinned: Spring would otherwise follow their package paths. */
  object ListenerOrder {
    const val ENTITY_CONTROL = 10
    const val HTTP_TICKETS = 20
    const val WORLD_INFO = 30
  }
}