package net.bestia.zone.item

import net.bestia.zone.BestiaException

class ItemNotFoundException(identifier: String) : BestiaException(
  code = "ITEM_NOT_FOUND",
  message = "Item $identifier was not found in the db"
)
