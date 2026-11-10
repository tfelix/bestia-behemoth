package net.bestia.zone.breeding

import net.bestia.zone.item.ecs.Inventory

/** An egg item as the live bag shows it: one copy, never stacked, never worn. */
object EggItems {

  fun inventoryItem(eggItemId: Long, weight: Int, instanceId: Long): Inventory.Item {
    return Inventory.Item(itemId = eggItemId, amount = 1, weight = weight, uniqueId = instanceId, stackable = false)
  }
}
