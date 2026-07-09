package net.bestia.zone.battle.skill

import net.bestia.zone.BestiaException

class InsufficientLevelException(
  requiredLevel: Int,
  currentLevel: Int
) : BestiaException(
  code = "INSUFFICIENT_LEVEL",
  message = "Requires level $requiredLevel, but current level is $currentLevel"
)
