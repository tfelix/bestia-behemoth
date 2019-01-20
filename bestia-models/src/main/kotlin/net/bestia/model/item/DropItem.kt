package net.bestia.model.item

import net.bestia.model.AbstractEntity
import net.bestia.model.bestia.Bestia
import java.io.Serializable
import javax.persistence.*

/**
 * Contains a list with items which are dropped by a bestia for a certain
 * probability. The probability ranges from 0.01% to 100%. The probability is
 * saved as a fixed point decimal.
 *
 * @author Thomas Felix
 */
@Entity
@Table(name = "drop_items")
data class DropItem(
    /**
     * Sets the item. Since there is some special treatment to equipment type
     * items the amount of equipment can only be 1 (equipment can not be stacked
     * inside the inventory, since additional information is attached to each
     * player item equipment).
     *
     * @param item
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ITEM_ID", nullable = false)
    val item: Item,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "BESTIA_ID", nullable = false)
    val bestia: Bestia,

    val probability: Int = 0
) : AbstractEntity(), Serializable {

  /**
   * Ctor.
   *
   * @param item
   * The dropped item.
   * @param bestia
   * The bestia who drops this item.
   * @param probability
   * The probability of the item drop. Can be between 0.01 and 100.
   */
  constructor(item: Item, bestia: Bestia, probability: Float): this(
      item, bestia, (probability * 100).toInt()
  )

  /**
   * The probability of an item drop. The value is in percentage from 0-100%.
   *
   * @return The probability from 0 to 100.
   */
  fun getProbability(): Float {
    return probability / 100.0f
  }
}
