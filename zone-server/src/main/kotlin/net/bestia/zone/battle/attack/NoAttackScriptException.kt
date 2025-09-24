package net.bestia.zone.battle.attack

import net.bestia.zone.BestiaException

class NoAttackScriptException : BestiaException(
  "NO_ATTACK_SCRIPT",
  "No attack script was attached to the battle context"
)