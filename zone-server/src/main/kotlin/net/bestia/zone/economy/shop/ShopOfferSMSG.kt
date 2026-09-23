package net.bestia.zone.economy.shop

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.ShopOfferSmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

/**
 * One settlement's shelves as a client draws them.
 *
 * A snapshot for a window, never a contract. Every trade is settled unit by unit against the live price
 * at the moment it resolves, so a client acting on a stale window pays what things cost now - which is
 * also why this is re-sent after every trade rather than being kept in step by deltas.
 */
data class ShopOfferSMSG(
  val settlement: Int,
  val merchantEntityId: EntityId,
  val entries: List<Entry>,
) : SMSG {

  /** @param offered what the town will part with: the shelves less what the locals keep back */
  data class Entry(val itemId: Long, val offered: Int, val buyPrice: Long, val sellPrice: Long)

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val offer = ShopOfferSmsgProto.ShopOfferSMSG.newBuilder()
      .setSettlement(settlement)
      .setMerchantEntityId(merchantEntityId)
      .addAllEntries(entries.map { entry ->
        ShopOfferSmsgProto.ShopEntry.newBuilder()
          .setItemId(entry.itemId)
          .setOffered(entry.offered)
          .setBuyPrice(entry.buyPrice)
          .setSellPrice(entry.sellPrice)
          .build()
      })
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setShopOffer(offer)
      .build()
  }
}
