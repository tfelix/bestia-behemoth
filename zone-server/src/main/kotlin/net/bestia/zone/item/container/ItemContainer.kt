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
import net.bestia.zone.item.equip.EquipmentSlot
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
 *
 * Worn equipment is *also* held here, marked by [ContainerSlot.equippedIn] rather than moved
 * elsewhere - see [equip]. Consequently, every removal path refuses to hand out a slot that is
 * currently worn: you cannot drop, trade away or consume what you are wearing without taking it
 * off first.
 *
 * Items promised to an open trade are marked the same way, by [ContainerSlot.reservedByTradeId] -
 * see [reserveInstance]. Every path that hands an item out or counts what is held therefore asks
 * [ContainerSlot.isFree] rather than looking only at wear, so an offered item cannot also be
 * dropped, eaten or fed to a craft while it sits in the trade window.
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
    // isFree, not merely unequipped: merging into a reserved pile would quietly enlarge an offer the
    // player has already locked, and merging into a worn one is meaningless.
    val existing = _slots.firstOrNull { it.isStackable && it.isFree && it.item?.id == item.id }
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
   * Wears an item in [slot]. Rejects (returns false) when the slot is already occupied, the item is
   * not held here, is not an [Item.ItemType.EQUIP], or belongs in a different slot. Callers that
   * also need to decide *whether the wearer is allowed* to wear it go through
   * [net.bestia.zone.item.equip.EquipmentService] first - this only enforces the item/container
   * rules.
   *
   * [uniqueId] names the exact instance to wear. It may legitimately be 0 for an item obtained this
   * session whose instance row was minted after the live ECS copy was made (see
   * [net.bestia.zone.ecs.item.Inventory.Item]); the item is then located by [itemId] instead,
   * picking any held, not-yet-worn copy - which is equivalent, since equipment is never stackable
   * and copies of one template are interchangeable at this point.
   */
  fun equip(itemId: Long, uniqueId: Long, slot: EquipmentSlot): Boolean {
    if (_slots.any { it.equippedIn == slot }) return false

    val candidate = if (uniqueId != 0L) {
      _slots.firstOrNull { it.uniqueId == uniqueId && it.isFree }
    } else {
      _slots.firstOrNull { !it.isStackable && it.isFree && it.template.id == itemId }
    } ?: return false

    val template = candidate.template
    if (template.type != Item.ItemType.EQUIP || template.equipSlot != slot) return false

    candidate.equippedIn = slot
    return true
  }

  /** Takes whatever is worn in [slot] off, returning its slot, or null if nothing was worn there. */
  fun unequip(slot: EquipmentSlot): ContainerSlot? {
    val worn = _slots.firstOrNull { it.equippedIn == slot } ?: return null
    worn.equippedIn = null
    return worn
  }

  /** Everything currently worn, keyed by the slot it is worn in. */
  fun equipped(): Map<EquipmentSlot, ContainerSlot> =
    _slots.mapNotNull { slot -> slot.equippedIn?.let { it to slot } }.toMap()

  /**
   * Removes [amount] of a plain stackable item identified by its template id. Returns false if not
   * enough is present. Does not touch instance slots.
   */
  fun removeStackable(itemId: Long, amount: Int): Boolean {
    require(amount > 0) { "amount > 0 required, was $amount" }
    val slot = _slots.firstOrNull { it.isStackable && it.isFree && it.item?.id == itemId } ?: return false
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
    if (!slot.isFree) return null
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
    val instanceSlot = _slots.firstOrNull { !it.isStackable && it.isFree && it.template.id == itemId }
    if (instanceSlot != null) {
      _slots.remove(instanceSlot)
      return RemovedItem(uniqueId = instanceSlot.uniqueId, instance = instanceSlot.itemInstance)
    }
    return if (removeStackable(itemId, amount)) RemovedItem(uniqueId = 0L, instance = null) else null
  }

  /** How much of [itemId] is actually available to spend - worn and promised copies do not count. */
  fun hasItem(itemId: Long, minAmount: Int = 1): Boolean {
    val held = _slots.filter { it.isFree && it.template.id == itemId }.sumOf { it.amount }
    return held >= minAmount
  }

  /**
   * Promises the instance held under [uniqueId] to [tradeId], leaving it here but out of reach of every
   * other path. Returns the marked slot, or null when it is not held, is worn, or is already promised.
   */
  fun reserveInstance(uniqueId: Long, tradeId: Long): ContainerSlot? {
    if (uniqueId == 0L) return null
    val slot = _slots.firstOrNull { it.uniqueId == uniqueId && it.isFree } ?: return null

    slot.reservedByTradeId = tradeId

    return slot
  }

  /**
   * Promises one non-stackable copy of [itemId] to [tradeId], for an instance item whose row was minted
   * after the live inventory copy was made and whose id the caller therefore cannot know yet. Equipment is
   * never stackable and copies of one template are interchangeable at this point, so any free one will do -
   * the same fallback [equip] makes, for the same reason.
   */
  fun reserveAnyInstance(itemId: Long, tradeId: Long): ContainerSlot? {
    val slot = _slots.firstOrNull { !it.isStackable && it.isFree && it.template.id == itemId } ?: return null

    slot.reservedByTradeId = tradeId

    return slot
  }

  /**
   * Promises [amount] of a plain stackable item to [tradeId] by **splitting** it off into a slot of its own:
   * the free pile shrinks and a new, marked slot holds exactly what was offered.
   *
   * Splitting rather than marking the whole pile is what lets a player offer 100 of their 500 arrows and go
   * on shooting the rest. It is also why [addStackable] and [removeStackable] have to skip reserved slots -
   * otherwise the next grant or spend would reach straight back into the offer.
   *
   * @return the new reserved slot, or null when that much is not freely held
   */
  fun reserveStackable(itemId: Long, amount: Int, tradeId: Long): ContainerSlot? {
    require(amount > 0) { "amount > 0 required, was $amount" }
    val source = _slots.firstOrNull { it.isStackable && it.isFree && it.template.id == itemId } ?: return null
    if (source.amount < amount) return null

    source.amount -= amount
    if (source.amount <= 0) {
      _slots.remove(source)
    }

    val reserved = ContainerSlot(container = this, item = source.template, amount = amount)
    reserved.reservedByTradeId = tradeId
    _slots.add(reserved)

    return reserved
  }

  /** Everything currently promised to [tradeId]. */
  fun reservedSlots(tradeId: Long): List<ContainerSlot> =
    _slots.filter { it.reservedByTradeId == tradeId }

  /**
   * Gives one promised slot back to its owner, merging a plain stack into whatever free pile of the same
   * template is already there so a retracted offer does not leave the inventory fragmented.
   *
   * @return false when [slotId] is not a slot promised to [tradeId]
   */
  fun releaseReservation(slotId: Long, tradeId: Long): Boolean {
    val slot = _slots.firstOrNull { it.id == slotId && it.reservedByTradeId == tradeId } ?: return false

    release(slot)

    return true
  }

  /** Gives every slot promised to [tradeId] back, for a trade that ended without an exchange. */
  fun releaseAllReservations(tradeId: Long) {
    reservedSlots(tradeId).forEach { release(it) }
  }

  private fun release(slot: ContainerSlot) {
    slot.reservedByTradeId = null

    if (!slot.isStackable) {
      return
    }

    // Fold the split pile back into whatever free one is already there, so retracting an offer does not
    // leave the inventory carrying two half stacks of the same thing.
    val target = _slots.firstOrNull { it !== slot && it.isStackable && it.isFree && it.item?.id == slot.template.id }
    if (target != null) {
      target.amount += slot.amount
      _slots.remove(slot)
    }
  }

  /**
   * Detaches a slot promised to [tradeId] so settlement can hand it to the other party. The [ItemInstance]
   * is intentionally kept alive - it is changing owner, not ceasing to exist.
   *
   * @return what was detached, or null when the slot is no longer promised to this trade
   */
  fun detachReserved(slotId: Long, tradeId: Long): DetachedItem? {
    val slot = _slots.firstOrNull { it.id == slotId && it.reservedByTradeId == tradeId } ?: return null

    _slots.remove(slot)

    return DetachedItem(template = slot.template, amount = slot.amount, instance = slot.itemInstance)
  }

  data class RemovedItem(val uniqueId: Long, val instance: ItemInstance?)

  /**
   * A slot lifted out of one container to be put into another. Carries the template as well as the optional
   * instance, because a plain stack has no instance to carry it.
   */
  data class DetachedItem(val template: Item, val amount: Int, val instance: ItemInstance?)

  enum class Type { MASTER, BESTIA, NPC, STORAGE, MAIL }
}
