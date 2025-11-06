package net.bestia.zone.item.script

import net.bestia.zone.BestiaException
import net.bestia.zone.item.Item

class ItemScriptNotFoundException(item: Item) : BestiaException(
  "ITEM_SCRIPT_NOT_FOUND",
  "Script for item ${item.identifier} does not exist"
)
