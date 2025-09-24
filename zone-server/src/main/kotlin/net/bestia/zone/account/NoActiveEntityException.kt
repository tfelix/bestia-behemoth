package net.bestia.zone.account

import net.bestia.zone.BestiaException

class NoActiveEntityException : BestiaException(
  code = "NO_ACTIVE_BESTIA",
  message = "No active entity for this player was found"
)