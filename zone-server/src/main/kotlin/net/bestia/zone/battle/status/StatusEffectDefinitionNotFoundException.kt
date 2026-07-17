package net.bestia.zone.battle.status

import net.bestia.zone.BestiaException

class StatusEffectDefinitionNotFoundException(id: Long) : BestiaException(
  code = "STATUS_EFFECT_DEFINITION_NOT_FOUND",
  message = "Status effect definition $id not found"
)
