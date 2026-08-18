package net.bestia.zone.ecs.item

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.InventoryComponentSMSGProto
import net.bestia.zone.message.EntitySMSG

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
        .setEquipped(item.equipped)
        .setDurability(item.durability)
        .setMaxDurability(item.maxDurability)
        .setSlots(item.slots)
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
    val amount: Int,
    val equipped: Boolean = false,
    val durability: Int = 0,
    val maxDurability: Int = 0,
    val slots: Int = 0
  )
}