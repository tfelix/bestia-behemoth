package net.bestia.zone.battle.buff

import net.bestia.zone.BestiaException

class BuffDefinitionNotFoundException(id: Long) : BestiaException(
  code = "BUFF_DEFINITION_NOT_FOUND",
  message = "Buff definition $id not found"
)
