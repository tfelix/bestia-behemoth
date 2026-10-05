package net.bestia.login.account

import net.bestia.account.KickReason

data class AccountSessionsTerminatedEvent(
  val accountId: Long,
  val reason: KickReason
)
