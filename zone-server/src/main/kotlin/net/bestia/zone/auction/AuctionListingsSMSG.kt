package net.bestia.zone.auction

import net.bestia.bnet.proto.AuctionListingsSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.message.SMSG
import java.time.Instant

data class AuctionListingsSMSG(
  val page: Int,
  val pageCount: Int,
  val mine: Boolean,
  val listings: List<Row>,
) : SMSG {

  /** @param goods what sits in the listing's escrow, with the copy's own state for the tooltip */
  data class Row(
    val listingId: Long,
    val goods: ReservedItem,
    val price: Long,
    val sellerName: String,
    val townName: String,
    val expiresAt: Instant,
    /** Null for a fixed price. */
    val bid: Bid? = null,
  )

  /** Where a timed auction stands. Zero is "none" for [current] and [buyout]. */
  data class Bid(val current: Long, val nextMin: Long, val buyout: Long, val leading: Boolean)

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val page = AuctionListingsSmsgProto.AuctionListingsSMSG.newBuilder()
      .setPage(page)
      .setPageCount(pageCount)
      .setMine(mine)
      .addAllListings(listings.map(::toProto))
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setAuctionListings(page)
      .build()
  }

  private fun toProto(row: Row): AuctionListingsSmsgProto.AuctionListing {
    return AuctionListingsSmsgProto.AuctionListing.newBuilder()
      .setListingId(row.listingId)
      .setItemId(row.goods.itemId)
      .setAmount(row.goods.amount)
      .setPrice(row.price)
      .setSellerName(row.sellerName)
      .setTownName(row.townName)
      .setExpiresAtEpochSeconds(row.expiresAt.epochSecond)
      .setIsAuction(row.bid != null)
      .setCurrentBid(row.bid?.current ?: 0)
      .setNextMinBid(row.bid?.nextMin ?: 0)
      .setBuyout(row.bid?.buyout ?: 0)
      .setLeading(row.bid?.leading == true)
      .setItemLevel(row.goods.itemLevel)
      .setUpgradeLevel(row.goods.upgradeLevel)
      .setDurability(row.goods.durability)
      .setMaxDurability(row.goods.maxDurability)
      .setSlots(row.goods.slots)
      .addAllRolledStats(row.goods.rolledStats.map { it.toProto() })
      .build()
  }
}
