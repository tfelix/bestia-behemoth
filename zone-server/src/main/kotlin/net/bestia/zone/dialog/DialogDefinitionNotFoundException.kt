package net.bestia.zone.dialog

import net.bestia.zone.BestiaException

class DialogDefinitionNotFoundException(id: Int) : BestiaException(
  code = "DIALOG_DEFINITION_NOT_FOUND",
  message = "Dialog definition $id not found"
)
