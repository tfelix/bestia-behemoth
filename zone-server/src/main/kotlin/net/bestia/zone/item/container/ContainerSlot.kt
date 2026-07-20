package net.bestia.zone.item.container

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import net.bestia.zone.item.Item
import net.bestia.zone.item.instance.ItemInstance

/**
 * One stack inside an [ItemContainer]. A slot references **either** a template [Item] (a plain,
 * stackable pile with an [amount]) **or** a unique [ItemInstance] (a single item carrying
 * per-instance state, [amount] always 1 and never merged). Exactly one of the two is set.
 *
 * The [itemInstance] association deliberately has no remove cascade: deleting a slot (e.g. dropping
 * the item to the ground) must not delete the instance row - the instance outlives its current
 * placement so its state survives the move.
 */
@Entity
@Table(name = "container_slot")
class ContainerSlot(
  @ManyToOne
  @JoinColumn(name = "container_id", nullable = false)
  val container: ItemContainer,

  @ManyToOne
  @JoinColumn(name = "item_id", nullable = true)
  val item: Item? = null,

  @ManyToOne
  @JoinColumn(name = "item_instance_id", nullable = true)
  val itemInstance: ItemInstance? = null,

  var amount: Int = 1,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  init {
    require((item == null) != (itemInstance == null)) {
      "A ContainerSlot must reference exactly one of item or itemInstance"
    }
    if (itemInstance != null) {
      require(amount == 1) { "An instance slot always holds exactly one item" }
    }
  }

  /** The template of whatever this slot holds, whether it is a plain stack or a unique instance. */
  val template: Item get() = itemInstance?.item ?: item!!

  /** The instance id, or 0 when this is a plain stackable slot (0 = "no unique instance"). */
  val uniqueId: Long get() = itemInstance?.id ?: 0L

  val isStackable: Boolean get() = itemInstance == null
}
