package net.bestia.zone.battle.attack

import net.bestia.zone.BestiaException

class AttackNotFoundException(id: Long) : BestiaException(
  code = "ATTACK_NOT_FOUND",
  message = "Attack $id not found"
)