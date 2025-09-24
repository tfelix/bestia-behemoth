package net.bestia.zone.account

import net.bestia.zone.BestiaException

class AccountNotFoundException : BestiaException(
  code = "NO_ACCOUNT",
  message = "The specified account was not found"
)