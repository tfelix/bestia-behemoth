package net.bestia.zone.component

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.InventoryComponentSMSGProto
import net.bestia.zone.message.entity.EntitySMSG

data class InventoryComponentSMSG(
  override val entityId: Long,
  val items: List<InventoryItem>
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val protoItems = items.map { item ->
      InventoryComponentSMSGProto.InventoryItem.newBuilder()
        .setItemId(item.itemId)
        .setUniqueId(item.uniqueId)
        .setAmount(item.amount)
        .build()
    }

    val inventoryComponent = InventoryComponentSMSGProto.InventoryComponentSMSG.newBuilder()
      .setEntityId(entityId)
      .addAllItems(protoItems)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompInventory(inventoryComponent)
      .build()
  }

  data class InventoryItem(
    val itemId: Int,
    val uniqueId: Long,
    val amount: Int
  )
}

