package net.bestia.zone.item.container

/**
 * One line of a trade offer, carrying everything a caller needs to mirror it without going back to the
 * database: the live [net.bestia.zone.ecs.item.Inventory] wants weight and the per-instance state, and the
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
) {

  companion object {
    fun of(slot: ContainerSlot): ReservedItem {
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
      )
    }
  }
}
