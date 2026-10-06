package net.bestia.zone.account

import net.bestia.zone.BestiaException

class OwnedBestiaPolicyViolationException(message: String) : BestiaException(
  code = "OWNED_BESTIA_POLICY_VIOLATION",
  message = message
)