package net.bestia.zone.item.container

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import net.bestia.zone.item.Item
import net.bestia.zone.item.instance.ItemInstance

/**
 * A first-class item container - the single place items are held. Masters, player bestias, NPCs,
 * storage and mail all own one, replacing the old master-anchored `inventory_item` model that
 * could not represent an item outside a master's inventory. Ground items are the one deliberate
 * exception: they live as spatial ECS entities rather than in a container (they still reference the
 * held [ItemInstance] by id so nothing is lost while an item is in transit).
 *
 * All stacking/merging logic lives here so it exists in exactly one place; persistence orchestration
 * (transactions, minting/loading [ItemInstance] rows) is done by
 * [net.bestia.zone.item.container.InventoryService].
 */
@Entity
@Table(name = "item_container")
class ItemContainer(
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  val type: Type,
) {

  @OneToMany(mappedBy = "container", cascade = [CascadeType.ALL], orphanRemoval = true)
  private val _slots: MutableSet<ContainerSlot> = mutableSetOf()

  val slots: List<ContainerSlot> get() = _slots.toList()

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  /**
   * Adds a plain, stackable item: merges into the existing plain stack of the same template if one
   * exists, otherwise starts a new stack. Never merges into an instance slot.
   */
  fun addStackable(item: Item, amount: Int) {
    require(amount > 0) { "amount > 0 required, was $amount" }
    val existing = _slots.firstOrNull { it.isStackable && it.item?.id == item.id }
    if (existing != null) {
      existing.amount += amount
    } else {
      _slots.add(ContainerSlot(container = this, item = item, amount = amount))
    }
  }

  /** Places a unique instance into its own non-merging slot. */
  fun addInstance(instance: ItemInstance) {
    _slots.add(ContainerSlot(container = this, itemInstance = instance))
  }

  /**
   * Removes [amount] of a plain stackable item identified by its template id. Returns false if not
   * enough is present. Does not touch instance slots.
   */
  fun removeStackable(itemId: Long, amount: Int): Boolean {
    require(amount > 0) { "amount > 0 required, was $amount" }
    val slot = _slots.firstOrNull { it.isStackable && it.item?.id == itemId } ?: return false
    if (slot.amount < amount) return false

    slot.amount -= amount
    if (slot.amount <= 0) {
      _slots.remove(slot)
    }
    return true
  }

  /**
   * Detaches the instance slot with the given [uniqueId] and returns its [ItemInstance] (the
   * instance row is intentionally kept alive - it just leaves this container). Returns null if no
   * such instance is held here.
   */
  fun takeInstance(uniqueId: Long): ItemInstance? {
    val slot = _slots.firstOrNull { it.uniqueId == uniqueId && uniqueId != 0L } ?: return null
    _slots.remove(slot)
    return slot.itemInstance
  }

  /**
   * Removes and returns one held item matching [itemId], preferring an instance slot so a unique
   * item's identity is preserved on e.g. a drop. Returns the detached [ItemInstance] (null for a
   * plain stackable) so the caller can carry its identity onward. [amount] is only used for the
   * stackable case; an instance always removes exactly one.
   */
  fun removeOne(itemId: Long, amount: Int): RemovedItem? {
    val instanceSlot = _slots.firstOrNull { !it.isStackable && it.template.id == itemId }
    if (instanceSlot != null) {
      _slots.remove(instanceSlot)
      return RemovedItem(uniqueId = instanceSlot.uniqueId, instance = instanceSlot.itemInstance)
    }
    return if (removeStackable(itemId, amount)) RemovedItem(uniqueId = 0L, instance = null) else null
  }

  fun hasItem(itemId: Long, minAmount: Int = 1): Boolean {
    val held = _slots.filter { it.template.id == itemId }.sumOf { it.amount }
    return held >= minAmount
  }

  data class RemovedItem(val uniqueId: Long, val instance: ItemInstance?)

  enum class Type { MASTER, BESTIA, NPC, STORAGE, MAIL }
}
