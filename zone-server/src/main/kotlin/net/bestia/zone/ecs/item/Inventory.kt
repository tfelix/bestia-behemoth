package net.bestia.zone.ecs.item

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.message.EntitySMSG

data class Inventory(
  private val items: MutableList<Item>
) : Component, Dirtyable {
  private var dirty = true

  /**
   * A single held stack. [uniqueId] is the id of the backing
   * [net.bestia.zone.item.instance.ItemInstance] (unique, upgradable/forgeable items); `0` means a
   * plain stackable item with no per-instance identity. [stackable] guards against merging a
   * freshly obtained instance item (e.g. equipment) whose backing instance id is not yet known
   * this session - such an item has [uniqueId] `0` but must still not stack.
   */
  class Item(
    val itemId: Long,
    var amount: Int,
    val weight: Int = 0,
    val uniqueId: Long = 0L,
    val stackable: Boolean = true
  ) {
    val isStackable: Boolean get() = stackable && uniqueId == 0L
    val totalWeight get() = amount * weight
  }

  val totalWeight get() = items.sumOf { it.totalWeight }

  // Add a single item
  fun addItem(item: Item) {
    if (item.isStackable) {
      val existing = items.find { it.itemId == item.itemId && it.isStackable }
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
  fun removeItem(itemId: Long): Boolean {
    val removed = items.removeIf { it.itemId == itemId }
    if (removed) {
      markDirty()
    }
    return removed
  }

  /** Removes the unique item with the given [uniqueId] (a non-stackable instance). */
  fun removeByUniqueId(uniqueId: Long): Boolean {
    if (uniqueId == 0L) return false
    val removed = items.removeIf { it.uniqueId == uniqueId }
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
  fun getItem(itemId: Int): Item? = items.find { it.itemId == itemId.toLong() }

  /**
   * Returns one held stack matching [itemId] to be dropped, preferring a unique instance so its
   * identity can be preserved on the ground.
   */
  fun findDroppable(itemId: Int): Item? =
    items.firstOrNull { it.itemId == itemId.toLong() && !it.isStackable }
      ?: items.firstOrNull { it.itemId == itemId.toLong() }

  // Get all items currently held
  fun getItems(): List<Item> = items.toList()

  // Get number of items
  fun size(): Int = items.size

  // Check if inventory is empty
  fun isEmpty(): Boolean = items.isEmpty()

  // Check if inventory contains an item with the given itemId
  fun hasItem(itemId: Int): Boolean = items.any { it.itemId == itemId.toLong() }

  // Update item amount by itemId (updates first match)
  fun updateItemAmount(itemId: Int, newAmount: Int): Boolean {
    val item = items.find { it.itemId == itemId.toLong() }
    if (item != null) {
      item.amount = newAmount
      markDirty()
      return true
    }
    return false
  }

  fun removeAmount(itemId: Int, amount: Int): Boolean {
    require(amount > 0) { "amount > 0 required, was $amount" }
    val item = items.singleOrNull { it.itemId == itemId.toLong() } ?: return false
    if (item.amount < amount) return false

    item.amount -= amount

    if (item.amount <= 0) {
      removeItem(itemId.toLong())
    } else {
      markDirty()
    }

    return true
  }

  fun decItem(itemId: Int): Boolean {
    val item = items.singleOrNull { it.itemId == itemId.toLong() }
    if (item != null) {
      item.amount -= 1

      if (item.amount <= 0) {
        removeItem(itemId.toLong())
      }

      markDirty()
      return true
    }

    return false
  }

  fun incItem(itemId: Int): Boolean {
    val item = items.singleOrNull { it.itemId == itemId.toLong() }
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

  override fun markDirty() {
    dirty = true
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return InventoryComponentSMSG(
      entityId = entityId,
      items = items.map { item ->
        InventoryComponentSMSG.InventoryItem(
          itemId = item.itemId.toInt(),
          uniqueId = item.uniqueId,
          amount = item.amount
        )
      }
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.OwnerOnly
  }
}
