package net.bestia.zone.ecs.item

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.Dirtyable
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
    val stackable: Boolean = true,
    var equipped: Boolean = false,

    /**
     * Mirrors the backing instance's wear so the client can draw it without a second round trip.
     * Both zero for a plain stack and for gear nobody gave a durability - see
     * [net.bestia.zone.item.instance.ItemInstance.maxDurability].
     */
    var durability: Int = 0,
    var maxDurability: Int = 0,

    /** Rune slots cut into the backing instance; zero for everything else. */
    var slots: Int = 0,

    /** Mirrors [net.bestia.zone.item.instance.ItemInstance.upgradeLevel]. */
    var upgradeLevel: Int = 0
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

  /**
   * Removes one non-stackable entry of [itemId], for a caller that has already decided - durably - that this
   * copy is leaving but cannot name it by [Item.uniqueId] because its instance row was minted after this
   * mirror was built.
   *
   * `firstOrNull`, not `singleOrNull`: holding two of the same piece of gear is ordinary, and refusing to
   * remove either of them would be the wrong answer.
   */
  fun removeInstanceOf(itemId: Long): Boolean {
    val item = items.firstOrNull { it.itemId == itemId && !it.isStackable } ?: return false

    items.remove(item)
    markDirty()

    return true
  }

  /**
   * Takes [amount] off the first plain stack of [itemId] that holds at least that much.
   *
   * Exists alongside [removeAmount] because that one uses `singleOrNull` and therefore silently does nothing
   * the moment a template is held both as a stack and as an instance - which is exactly the situation a trade
   * or a drop is most likely to meet.
   */
  fun removeFromStack(itemId: Long, amount: Int): Boolean {
    require(amount > 0) { "amount > 0 required, was $amount" }
    val item = items.firstOrNull { it.itemId == itemId && it.isStackable && it.amount >= amount } ?: return false

    item.amount -= amount
    if (item.amount <= 0) {
      items.remove(item)
    }
    markDirty()

    return true
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

  /**
   * Flips the equipped marker on the held item backed by [uniqueId], mirroring what
   * [net.bestia.zone.item.equip.EquipmentSlot] it now sits in (or was taken out of) so the client's
   * inventory view can show gear as worn without cross-referencing the Equipment component. No-op
   * (and does not dirty) when the item is not held or already at the requested state.
   */
  fun setEquipped(uniqueId: Long, equipped: Boolean): Boolean {
    if (uniqueId == 0L) return false
    val item = items.find { it.uniqueId == uniqueId } ?: return false
    if (item.equipped == equipped) return false

    item.equipped = equipped
    markDirty()

    return true
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

  /**
   * Mirrors a change made to the backing [net.bestia.zone.item.instance.ItemInstance] - a repair, a
   * successful upgrade, a freshly cut rune slot.
   *
   * Named for *what changed* rather than offering a setter per field, because a craft resolves into
   * exactly one of these three and the caller has the whole new state in hand either way. Returns false
   * when [uniqueId] is not held here, which is what tells a resolving craft the item left in the
   * meantime.
   */
  fun updateInstanceState(uniqueId: Long, durability: Int, slots: Int, upgradeLevel: Int): Boolean {
    if (uniqueId == 0L) return false
    val item = items.find { it.uniqueId == uniqueId } ?: return false

    item.durability = durability
    item.slots = slots
    item.upgradeLevel = upgradeLevel
    markDirty()

    return true
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
          amount = item.amount,
          equipped = item.equipped,
          durability = item.durability,
          maxDurability = item.maxDurability,
          slots = item.slots,
          upgradeLevel = item.upgradeLevel
        )
      }
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.OwnerOnly
  }
}
