package net.bestia.zone.account.master.status

import net.bestia.zone.BestiaException

class NoStatusPointsAvailableException(masterId: Long) : BestiaException(
  code = "NO_STATUS_POINTS_AVAILABLE",
  message = "Master $masterId has no status points available to spend"
)
