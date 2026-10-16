package net.bestia.zone.item.ecs

import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.bnet.proto.WornGearComponentSMSGProto
import net.bestia.zone.message.EntitySMSG

/** [items] pairs an [net.bestia.zone.item.equip.EquipmentSlot] ordinal with the item id worn there. */
data class WornGearComponentSMSG(
  override val entityId: Long,
  val items: List<Pair<Int, Int>>
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val wornGear = WornGearComponentSMSGProto.WornGearComponentSMSG.newBuilder()
      .addAllItems(items.map { (slot, itemId) ->
        WornGearComponentSMSGProto.WornItem.newBuilder().setSlot(slot).setItemId(itemId).build()
      })
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setWornGear(wornGear))
  }
}
