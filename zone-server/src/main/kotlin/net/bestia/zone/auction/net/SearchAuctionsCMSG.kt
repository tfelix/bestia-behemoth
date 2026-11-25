package net.bestia.zone.auction.net

import net.bestia.bnet.proto.SearchAuctionsCmsgProto
import net.bestia.zone.auction.AuctionCategory
import net.bestia.zone.auction.AuctionQuery
import net.bestia.zone.item.essence.GemTier
import net.bestia.zone.message.CMSG

data class SearchAuctionsCMSG(
  override val playerId: Long,
  val query: AuctionQuery,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: SearchAuctionsCmsgProto.SearchAuctionsCMSG): SearchAuctionsCMSG {
      return SearchAuctionsCMSG(
        playerId = accountId,
        query = AuctionQuery(
          houseEntityId = msg.houseEntityId,
          category = AuctionCategory.fromProto(msg.category),
          minLevel = msg.minLevel,
          maxLevel = msg.maxLevel,
          itemIds = msg.itemIdsList,
          mine = msg.mine,
          page = msg.page,
          essence = essenceFilterOf(msg),
        ),
      )
    }

    /** An unknown tier name filters for nothing rather than for every gem. */
    private fun essenceFilterOf(msg: SearchAuctionsCmsgProto.SearchAuctionsCMSG): AuctionQuery.EssenceFilter? {
      if (!msg.withEssence) return null
      val tier = msg.essenceTier.takeIf { it.isNotEmpty() }?.let { name -> GemTier.entries.find { it.name == name } }
      if (msg.essenceTier.isNotEmpty() && tier == null) return AuctionQuery.EssenceFilter(listOf(NO_SPECIES), null)

      return AuctionQuery.EssenceFilter(msg.essenceBestiaIdsList, tier)
    }

    private const val NO_SPECIES = -1L
  }
}
