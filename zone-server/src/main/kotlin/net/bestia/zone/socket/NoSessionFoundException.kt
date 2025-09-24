package net.bestia.zone.socket

import net.bestia.zone.BestiaException
import net.bestia.zone.util.AccountId

class NoSessionFoundException(accountId: AccountId) : BestiaException(
  code = "NO_SESSION",
  message = "No session found for account $accountId"
)