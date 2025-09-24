package net.bestia.zone.account

import net.bestia.zone.BestiaEvent

class AccountConnectedEvent(
  source: Any,
  val accountId: Long,
) : BestiaEvent(source)