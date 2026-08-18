package net.bestia.zone.skill

import net.bestia.zone.BestiaException

class MasterRitualNotPerformedException(masterId: Long, tree: String) : BestiaException(
  code = "MASTER_RITUAL_NOT_PERFORMED",
  message = "Master $masterId cannot invest in tree $tree before performing the Master Ritual"
)
