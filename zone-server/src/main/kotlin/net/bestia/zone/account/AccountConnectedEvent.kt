package net.bestia.zone.account

import net.bestia.account.Authority
import net.bestia.zone.BestiaEvent

class AccountConnectedEvent(
  source: Any,
  val accountId: Long,
  val authorities: Set<Authority>,
) : BestiaEvent(source)