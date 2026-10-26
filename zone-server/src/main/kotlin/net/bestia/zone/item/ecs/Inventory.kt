package net.bestia.zone.item.ecs

import net.bestia.zone.item.ItemProvenance
import net.bestia.zone.item.PlayerItemIds
import net.bestia.zone.item.stat.StatValue
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.sync.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.message.EntitySMSG

data class Inventory(
  private val items: MutableList<Item>
) : Component, Dirtyable {
  override val dirtyFlag = DirtyFlag()

  /**
   * A single held stack. [uniqueId] is the id of the backing
   * [net.bestia.zone.item.persistence.ItemInstance] (unique, upgradable/forgeable items); `0` means a
   * plain stackable item with no per-instance identity.
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
     * [net.bestia.zone.item.persistence.ItemInstance.maxDurability].
     */
    var durability: Int = 0,
    var maxDurability: Int = 0,

    /** Sockets of the backing instance; zero for everything else. */
    var slots: Int = 0,

    /** Mirrors [net.bestia.zone.item.persistence.ItemInstance.upgradeLevel]. */
    var upgradeLevel: Int = 0,

    /** This copy's item level, or 0 for a stack and an old copy: their level is the template's. */
    var itemLevel: Int = 0,

    val rolledStats: List<StatValue> = emptyList(),

    /** Who crafted this pile or copy and where; null for a plain one. Piles with different makers never merge. */
    val provenance: ItemProvenance? = null
  ) {
    /**
     * What the player names this held item by. An instance is named by its own id. A pile has no row to name
     * it by, so it gets a handle that lives as long as the pile: a merge keeps the handle of the pile it lands
     * on.
     */
    val playerItemId: Long = if (uniqueId != 0L) uniqueId else PlayerItemIds.next()

    val isStackable: Boolean get() = stackable && uniqueId == 0L
    val totalWeight get() = amount * weight
  }

  val totalWeight get() = items.sumOf { it.totalWeight }

  // Add a single item
  fun addItem(item: Item) {
    if (item.isStackable) {
      val existing = items.find { it.itemId == item.itemId && it.isStackable && it.provenance == item.provenance }
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
   * Takes [amount] of [itemId] across every plain stack of it, or nothing at all.
   *
   * Exists alongside [removeAmount] because that one uses `singleOrNull` and therefore silently does nothing
   * the moment a template is held both as a stack and as an instance, or in two piles - which is exactly the
   * situation a trade or a drop is most likely to meet.
   */
  fun removeFromStack(itemId: Long, amount: Int): Boolean = takeAcrossPiles(itemId, amount) != null

  /**
   * [removeFromStack] that answers what it took, one entry per pile, so a caller that has to give the items back
   * can give back the same makers. Piles are taken in [ItemProvenance.TAKE_ORDER], as the database takes them.
   */
  fun takeAcrossPiles(itemId: Long, amount: Int): List<Item>? {
    require(amount > 0) { "amount > 0 required, was $amount" }
    val piles = items.filter { it.itemId == itemId && it.isStackable }
      .sortedWith(compareBy(ItemProvenance.TAKE_ORDER) { it.provenance })
    if (piles.sumOf { it.amount } < amount) return null

    val takenParts = mutableListOf<Item>()
    var remaining = amount
    for (pile in piles) {
      val taken = minOf(pile.amount, remaining)
      pile.amount -= taken
      remaining -= taken
      takenParts += Item(itemId = itemId, amount = taken, weight = pile.weight, provenance = pile.provenance)
      if (pile.amount <= 0) {
        items.remove(pile)
      }
      if (remaining == 0) break
    }
    markDirty()

    return takenParts
  }

  /**
   * Takes off this live bag what the database just removed: [amount] of a pile when [uniqueId] is 0, else one
   * instance. Answers the amount that left, or null if it was not here.
   */
  fun mirrorRemoval(itemId: Long, amount: Int, uniqueId: Long): Int? {
    if (uniqueId == 0L) {
      return if (removeFromStack(itemId, amount)) amount else null
    }

    return if (removeByUniqueId(uniqueId)) 1 else null
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

  // Get all items currently held
  fun getItems(): List<Item> = items.toList()

  fun byPlayerItemId(playerItemId: Long): Item? = items.firstOrNull { it.playerItemId == playerItemId }

  /**
   * Takes [amount] off the held item the player named: from its pile, or the instance itself. Answers false and
   * changes nothing when that much of it is not held.
   */
  fun take(playerItemId: Long, amount: Int): Boolean {
    require(amount > 0) { "amount > 0 required, was $amount" }
    val held = byPlayerItemId(playerItemId) ?: return false
    if (held.amount < amount) return false

    held.amount -= amount
    if (held.amount <= 0) {
      items.remove(held)
    }
    markDirty()

    return true
  }

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
   * Mirrors a change made to the backing [net.bestia.zone.item.persistence.ItemInstance] - a repair, a
   * successful upgrade, a freshly cut rune slot.
   *
   * Named for *what changed* rather than offering a setter per field, because a craft resolves into
   * exactly one of these three and the caller has the whole new state in hand either way. Returns false
   * when [uniqueId] is not held here, which is what tells a resolving craft the item left in the
   * meantime.
   */
  fun updateInstanceState(uniqueId: Long, durability: Int, slots: Int, upgradeLevel: Int, itemLevel: Int): Boolean {
    if (uniqueId == 0L) return false
    val item = items.find { it.uniqueId == uniqueId } ?: return false

    item.durability = durability
    item.slots = slots
    item.upgradeLevel = upgradeLevel
    item.itemLevel = itemLevel
    markDirty()

    return true
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
          upgradeLevel = item.upgradeLevel,
          itemLevel = item.itemLevel,
          rolledStats = item.rolledStats,
          playerItemId = item.playerItemId,
          provenance = item.provenance
        )
      }
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.OwnerOnly
  }
}
