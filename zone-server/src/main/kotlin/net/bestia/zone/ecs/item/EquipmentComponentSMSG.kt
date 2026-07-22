package net.bestia.zone.ecs.item

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.EquipmentComponentSMSGProto
import net.bestia.zone.message.EntitySMSG

data class EquipmentComponentSMSG(
  override val entityId: Long,
  val items: List<EquippedItem>
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val protoItems = items.map { item ->
      EquipmentComponentSMSGProto.EquippedItem.newBuilder()
        .setSlot(item.slot)
        .setItemId(item.itemId)
        .setUniqueId(item.uniqueId)
        .build()
    }

    val equipmentComponent = EquipmentComponentSMSGProto.EquipmentComponentSMSG.newBuilder()
      .setEntityId(entityId)
      .addAllItems(protoItems)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompEquipment(equipmentComponent)
      .build()
  }

  /** [slot] is an [net.bestia.zone.item.equip.EquipmentSlot] ordinal. */
  data class EquippedItem(
    val slot: Int,
    val itemId: Int,
    val uniqueId: Long
  )
}
