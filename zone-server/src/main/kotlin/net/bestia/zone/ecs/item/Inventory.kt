package net.bestia.zone.ecs.item

import net.bestia.zone.component.InventoryComponentSMSG
import net.bestia.zone.ecs.Component
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.message.entity.EntitySMSG


data class Inventory(
  private val items: MutableList<Item>
) : Component, Dirtyable {
  private var dirty = true

  class Item(
    val itemId: Int,
    val amount: Int,
    val uniqueId: Long = 0 // 0 means nothing special.
  )

  // Add a single item
  fun addItem(item: Item) {
    items.add(item)
    markDirty()
  }

  // Add multiple items
  fun addItems(itemsToAdd: Collection<Item>) {
    items.addAll(itemsToAdd)
    markDirty()
  }

  // Remove item by itemId (removes first match)
  fun removeItem(itemId: Int): Boolean {
    val removed = items.removeIf { it.itemId == itemId }
    if (removed) {
      markDirty()
    }
    return removed
  }

  // Remove items matching predicate
  fun removeItemsIf(predicate: (Item) -> Boolean): Boolean {
    val removed = items.removeIf(predicate)
    if (removed) {
      markDirty()
    }
    return removed
  }

  // Clear all items
  fun clearItems() {
    if (items.isNotEmpty()) {
      items.clear()
      markDirty()
    }
  }

  // Get item by itemId (returns first match)
  fun getItem(itemId: Int): Item? = items.find { it.itemId == itemId }


  // Get number of items
  fun size(): Int = items.size

  // Check if inventory is empty
  fun isEmpty(): Boolean = items.isEmpty()

  // Check if inventory contains an item with the given itemId
  fun hasItem(itemId: Int): Boolean = items.any { it.itemId == itemId }

  // Update item amount by itemId (updates first match)
  fun updateItemAmount(itemId: Int, newAmount: Int): Boolean {
    val item = items.find { it.itemId == itemId }
    if (item != null) {
      val index = items.indexOf(item)
      items[index] = Item(item.itemId, newAmount, item.uniqueId)
      markDirty()
      return true
    }
    return false
  }

  override fun isDirty(): Boolean {
    return dirty
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long): EntitySMSG {
    return InventoryComponentSMSG(
      entityId = entityId,
      items = items.map { item ->
        InventoryComponentSMSG.InventoryItem(
          itemId = item.itemId,
          uniqueId = item.uniqueId,
          amount = item.amount
        )
      }
    )
  }

  override fun broadcastType(): Dirtyable.BroadcastType {
    return Dirtyable.BroadcastType.ONLY_OWNER
  }

  fun markDirty() {
    dirty = true
  }
}