package net.bestia.zone.ecs.item

import net.bestia.zone.component.InventoryComponentSMSG
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.SyncContext
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.player.Account
import net.bestia.zone.message.entity.EntitySMSG

data class Inventory(
  private val items: MutableList<Item>
) : Component, Dirtyable {
  private var dirty = true

  class Item(
    val itemId: Int,
    var amount: Int,
    val uniqueId: Long = 0 // 0 means nothing special.
  )

  // Add a single item
  fun addItem(item: Item) {
    val isStackable = item.uniqueId == 0L
    if (isStackable) {
      val existing = items.find { it.itemId == item.itemId && it.uniqueId == 0L }
      if (existing != null) {
        existing.amount += item.amount
      } else {
        items.add(item)
      }
    } else {
      items.add(item)
    }

    markDirty()
  }

  // Add multiple items
  fun addItems(itemsToAdd: Collection<Item>) {
    itemsToAdd.forEach { addItem(it) }
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

  fun removeAmount(itemId: Int, amount: Int): Boolean {
    require(amount > 0)
    val item = items.singleOrNull { it.itemId == itemId } ?: return false
    if (item.amount < amount) return false

    item.amount -= amount

    if (item.amount <= 0) {
      removeItem(itemId)
    } else {
      markDirty()
    }

    return true
  }

  fun decItem(itemId: Int): Boolean {
    val item = items.singleOrNull { it.itemId == itemId }
    if (item != null) {
      item.amount -= 1

      if (item.amount <= 0) {
        removeItem(itemId)
      }

      markDirty()
      return true
    }

    return false
  }

  fun incItem(itemId: Int): Boolean {
    val item = items.singleOrNull { it.itemId == itemId }
    if (item != null) {
      item.amount += 1

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

  override fun syncTargets(context: SyncContext, entityId: EntityId): SyncTargets {
    val owner = context.world.get(entityId, Account::class)?.accountId
    return SyncTargets.Accounts(setOfNotNull(owner))
  }

  fun markDirty() {
    dirty = true
  }
}