package net.bestia.zone.auction.net

import net.bestia.bnet.proto.SearchAuctionsCmsgProto
import net.bestia.zone.auction.AuctionCategory
import net.bestia.zone.auction.AuctionQuery
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
        ),
      )
    }
  }
}
