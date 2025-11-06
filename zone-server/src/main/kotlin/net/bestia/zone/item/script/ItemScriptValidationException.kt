package net.bestia.zone.item.script

import net.bestia.zone.BestiaException

class ItemScriptValidationException(message: String) : BestiaException(
  "ITEM_SCRIPT_VALIDATION_FAILED",
  message
)
