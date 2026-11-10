package net.bestia.zone.skill

import net.bestia.zone.BestiaException

/** The Novice tree closes with the first point spent in any other tree. */
class NoviceTreeClosedException(masterId: Long) : BestiaException(
  code = "NOVICE_TREE_CLOSED",
  message = "Master $masterId has invested outside the Novice tree, which closes it"
)
