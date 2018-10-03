package net.bestia.zoneserver.entity.component

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.model.domain.Item
import java.lang.IllegalStateException

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
data class InventoryComponent(
    override val entityId: Long
) : Component {

  private val items: MutableMap<Long, InventoryItem> = mutableMapOf()

  /**
   * Gives the maximum carryable weight of this entity. It is pos. infinite if
   * its unlimited.
   *
   * @return The maximum item weight.
   */
  @JsonProperty("mw")
  var maxWeight: Float = 0f
    set(value) {
      if (maxWeight < 0) {
        throw IllegalArgumentException("maxWeight can not be negative.")
      }
      field = value
    }

  /**
   * The maximum number of items for this unit. Or -1 of its unlimited.
   *
   * @return The maximum item count. -1 if unlimited.
   */
  @JsonProperty("mc")
  var maxItemCount: Int = 0

  /**
   * The current item weight in kg. The item weight per item is saved as 0.1kg
   * per unit.
   *
   * @return Current item weight.
   */
  @JsonProperty("w")
  var weight: Float = 0f

  /**
   * The current number of item (slots) stored in this entity.
   *
   * @return Current number of item slots stored in this entity.
   */
  val itemCount: Int
    @JsonProperty("c")
    get() = items.size

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
    throw IllegalStateException("Not implemented")
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
