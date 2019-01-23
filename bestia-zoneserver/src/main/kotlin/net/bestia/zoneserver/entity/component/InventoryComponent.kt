package net.bestia.zoneserver.entity.component

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.model.item.PlayerItemId

data class InventoryItem(
    val amount: Int,
    val weight: Int,
    val playerItemId: PlayerItemId
) {
  val totalWeight: Int
    get() = amount * weight
}

/**
 * Entities having this trait can be loaded with a certain amount of items into
 * their local inventory and use this information to micro manage their own
 * inventory.
 *
 * @author Thomas Felix
 */
data class InventoryComponent(
    override val entityId: Long,

    /**
     * Gives the maximum carryable weight of this entity. It is pos. infinite if
     * its unlimited. Its unit is measured in 0.1 increments of a kg.
     *
     * @return The maximum item weight.
     */
    @JsonProperty("mw")
    val maxWeight: Int = 100,

    /**
     * The maximum number of items for this unit. Or -1 of its unlimited.
     *
     * @return The maximum item count. -1 if unlimited.
     */
    @JsonProperty("mc")
    val maxItemCount: Int = 100,

    val items: List<InventoryItem> = emptyList()
) : Component {

  init {
    if (totalWeight > maxWeight) {
      throw IllegalArgumentException("Current weight ($totalWeight) is bigger then max weight ($maxWeight)")
    }

    if (items.size > maxItemCount) {
      throw IllegalArgumentException("Item slots used (${items.size} is bigger then max item slots ($maxItemCount)")
    }
  }

  /**
   * The current total item weight in units of 0.1 kg.
   *
   * @return Current item weight.
   */
  @get:JsonProperty("w")
  val totalWeight: Int
    get() = items.sumBy { it.totalWeight }

  companion object {
    /**
     * Constant for meaning that the item count is basically not limited.
     */
    const val UNLIMITED_ITEMS = -1
  }
}
