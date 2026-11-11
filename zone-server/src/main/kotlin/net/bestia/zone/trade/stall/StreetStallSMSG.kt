package net.bestia.zone.trade.stall

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.StreetStallSmsgProto
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.message.SMSG

data class StreetStallSMSG(
  val stallEntityId: Long,
  val sellerName: String,
  val mine: Boolean,
  val maxListings: Int,
  val closesInSeconds: Int,
  val closed: Boolean,
  val rows: List<Row>,
) : SMSG {

  data class Row(val listingId: Long, val goods: ReservedItem, val price: Long)

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val stall = StreetStallSmsgProto.StreetStallSMSG.newBuilder()
      .setStallEntityId(stallEntityId)
      .setSellerName(sellerName)
      .setMine(mine)
      .setMaxListings(maxListings)
      .setClosesInSeconds(closesInSeconds)
      .setClosed(closed)
      .addAllListings(rows.map(::toProto))

    return EnvelopeProto.Envelope.newBuilder().setStreetStall(stall).build()
  }

  private fun toProto(row: Row): StreetStallSmsgProto.StallListing {
    return StreetStallSmsgProto.StallListing.newBuilder()
      .setListingId(row.listingId)
      .setItemId(row.goods.itemId)
      .setAmount(row.goods.amount)
      .setPrice(row.price)
      .setItemLevel(row.goods.itemLevel)
      .setUpgradeLevel(row.goods.upgradeLevel)
      .setDurability(row.goods.durability)
      .setMaxDurability(row.goods.maxDurability)
      .setSlots(row.goods.slots)
      .addAllRolledStats(row.goods.rolledStats.map { it.toProto() })
      .build()
  }

  companion object {
    fun closed(stallEntityId: Long) = StreetStallSMSG(stallEntityId, "", false, 0, 0, true, emptyList())
  }
}
