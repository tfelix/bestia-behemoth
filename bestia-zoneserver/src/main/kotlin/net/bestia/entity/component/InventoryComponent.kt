package net.bestia.entity.component

import net.bestia.model.domain.Item
import java.util.*

data class InventoryItem(
        val amount: Int,
        val itemId: Int
)

/**
 * Entities having this trait can be loaded with a certain amount of items into
 * their local inventory and use this information to micro manage their own
 * inventory.
 *
 * @author Thomas Felix
 */
@ComponentSync(SyncType.OWNER)
class InventoryComponent(id: Long) : Component(id) {

  /**
   * Gives the maximum carryable weight of this entity. It is pos. infinite if
   * its unlimited.
   *
   * @return The maximum item weight.
   */
  var maxWeight: Float = 0f
    set(value) {
      if (maxWeight < 0) {
        throw IllegalArgumentException("MaxWeight can not be negative.")
      }
      field = value
    }

  /**
   * The maximum number of items for this unit. Or -1 of its unlimited.
   *
   * @return The maximum item count. -1 if unlimited.
   */
  var maxItemCount: Int = 0

  private val items = mutableListOf<InventoryItem>()

  /**
   * The current item weight in kg. The item weight per item is saved as 0.1kg
   * per unit.
   *
   * @return Current item weight.
   */
  var weight: Float = 0f

  /**
   * The current number of item (slots) stored in this entity.
   *
   * @return Current number of item slots stored in this entity.
   */
  val itemCount: Int
    get() = items.size

  init {
    clear()
  }

  override fun clear() {
    items.clear()
    maxItemCount = UNLIMITED_ITEMS
    maxWeight = 0f
  }

  /**
   * Sets the maximum weight this entity can carry.
   *
   * @param maxWeight The maximum weight in units. One unit is 0.1kg.
   */

  /**
   * Adds the new item to the inventory. But this will work only if the number
   * of item slots are not exceeded and the total amount of weight does not be
   * bigger as the maximum amount of weight.
   *
   * @param item   The item to be added.
   * @param amount The amount of the item to be added to the inventory.
   * @return TRUE if the item has been added to the inventory. FALSE
   * otherwise.
   */
  fun addItem(item: Item, amount: Int): Boolean {
    // Check count.
    if (itemCount + 1 > maxItemCount) {
      return false
    }

    // Check weight.
    if (item.weight / .1f + weight > maxWeight) {
      return false
    }

    items.add(InventoryItem(amount, item.id))
    return true
  }

  /**
   * Removes the item from the inventory. It returns true only if the item
   * could be successfully be removed.
   *
   * @param item
   * @param amount
   * @return
   */
  fun removeItem(item: Item, amount: Int): Boolean {
    val inventoryItem = items.stream()
            .filter { x -> x.itemId == item.id }
            .findFirst()
    if (inventoryItem.isPresent) {

      val ic = inventoryItem.get()

      return when {
        ic.amount > amount -> {
          items.remove(ic)
          items.add(ic.copy(amount = ic.amount - amount))
          true
        }
        ic.amount == amount -> {
          items.remove(ic)
          true
        }
        else -> false
      }
    } else {
      return false
    }
  }

  override fun hashCode(): Int {
    return Objects.hash(items, maxItemCount, maxWeight)
  }

  override fun equals(obj: Any?): Boolean {
    if (this === obj) {
      return true
    }
    if (!super.equals(obj)) {
      return false
    }
    if (obj !is InventoryComponent) {
      return false
    }
    val other = obj as InventoryComponent?
    return (items == other!!.items
            && maxItemCount == other.maxItemCount
            && maxWeight == other.maxWeight)
  }

  override fun toString(): String {
    return "InventoryComp[maxWeight: $maxWeight, items: ${items.size}/$maxItemCount]"
  }

  companion object {

    /**
     * Constant for meaning that the item count is basically not limited.
     */
    const val UNLIMITED_ITEMS = -1
  }
}
