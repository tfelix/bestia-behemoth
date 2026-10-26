package net.bestia.zone.item.container

import net.bestia.zone.item.ItemProvenance
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.stat.StatValue
import net.bestia.zone.item.persistence.ContainerSlot

/**
 * One line of goods held out of an inventory - a trade offer, later an auction listing - carrying everything a caller needs to mirror it without going back to the
 * database: the live [net.bestia.zone.item.ecs.Inventory] wants weight and the per-instance state, and the
 * trade window wants the same numbers again to draw a wear bar.
 *
 * [offerSlotId] is the [ContainerSlot] the line came from. It names the offer on the wire, so that two lines
 * of the same template stay distinguishable when one of them is retracted.
 */
data class ReservedItem(
  val offerSlotId: Long,
  val itemId: Long,
  val amount: Int,
  val weight: Int,
  val uniqueId: Long,
  val stackable: Boolean,
  val durability: Int,
  val maxDurability: Int,
  val slots: Int,
  val upgradeLevel: Int,
  val itemLevel: Int,
  val rolledStats: List<StatValue>,
  val provenance: ItemProvenance? = null,
) {

  fun toLiveItem(): Inventory.Item {
    return Inventory.Item(
      itemId = itemId,
      amount = amount,
      weight = weight,
      uniqueId = uniqueId,
      stackable = stackable,
      durability = durability,
      maxDurability = maxDurability,
      slots = slots,
      upgradeLevel = upgradeLevel,
      itemLevel = itemLevel,
      rolledStats = rolledStats,
      provenance = provenance,
    )
  }

  companion object {
    /** A plain stack with no instance behind it, such as coins. */
    fun stack(itemId: Long, amount: Int, weight: Int): ReservedItem {
      return ReservedItem(
        offerSlotId = 0L, itemId = itemId, amount = amount, weight = weight, uniqueId = 0L, stackable = true,
        durability = 0, maxDurability = 0, slots = 0, upgradeLevel = 0, itemLevel = 0, rolledStats = emptyList(),
      )
    }

    fun of(slot: ContainerSlot, itemLevel: Int): ReservedItem {
      val template = slot.template

      return ReservedItem(
        offerSlotId = slot.id,
        itemId = template.id,
        amount = slot.amount,
        weight = template.weight,
        uniqueId = slot.uniqueId,
        stackable = slot.isStackable,
        durability = slot.durability,
        maxDurability = slot.maxDurability,
        slots = slot.slots,
        upgradeLevel = slot.itemInstance?.upgradeLevel ?: 0,
        itemLevel = itemLevel,
        rolledStats = slot.rolledStats,
        provenance = slot.heldProvenance,
      )
    }
  }
}
