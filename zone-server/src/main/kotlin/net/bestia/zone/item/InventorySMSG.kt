package net.bestia.zone.item

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.InventoryCmsgProto
import net.bestia.zone.message.SMSG

data class InventorySMSG(
  val entityId: Long,
  val items: List<PlayerItem>
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val protoItems = items.map { item ->
      InventoryCmsgProto.PlayerItem.newBuilder()
        .setItemId(item.itemId)
        .setPlayerItemId(item.playerItemId)
        .setAmount(item.amount)
        .build()
    }

    val inventorySMSG = InventoryCmsgProto.InventorySMSG.newBuilder()
      .setEntityId(entityId.toULong().toLong())
      .addAllItems(protoItems)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setInventory(inventorySMSG)
      .build()
  }

  data class PlayerItem(
    val itemId: Int,
    val playerItemId: Long,
    val amount: Int
  )
}
