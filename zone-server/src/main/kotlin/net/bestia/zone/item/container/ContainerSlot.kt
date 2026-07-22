package net.bestia.zone.item.container

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import net.bestia.zone.item.Item
import net.bestia.zone.item.equip.EquipmentSlot
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

  /**
   * The equipment slot this item is currently worn in, or null when it just sits in the inventory.
   * Worn gear deliberately stays in its owner's container rather than moving somewhere else: carry
   * weight keeps counting it and the existing `Master`/`PlayerBestia` container persistence covers
   * equipment for free. Only [ItemContainer] ever writes this, so the "one item per slot" rule
   * lives in exactly one place.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "equipped_in", nullable = true)
  var equippedIn: EquipmentSlot? = null
    internal set

  val isEquipped: Boolean get() = equippedIn != null

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
