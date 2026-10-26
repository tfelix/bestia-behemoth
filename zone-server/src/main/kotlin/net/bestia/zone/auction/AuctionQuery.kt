package net.bestia.zone.auction

/** One page of the market a player asked for. Null and empty fields mean "any". */
data class AuctionQuery(
  val houseEntityId: Long,
  val category: AuctionCategory?,
  val minLevel: Int,
  val maxLevel: Int,
  val itemIds: List<Long>,
  val mine: Boolean,
  val page: Int,
)
