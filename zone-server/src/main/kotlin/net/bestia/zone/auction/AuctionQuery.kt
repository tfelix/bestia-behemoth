package net.bestia.zone.auction

import net.bestia.zone.item.essence.GemTier

/** One page of the market a player asked for. Null and empty fields mean "any". */
data class AuctionQuery(
  val houseEntityId: Long,
  val category: AuctionCategory?,
  val minLevel: Int,
  val maxLevel: Int,
  val itemIds: List<Long>,
  val mine: Boolean,
  val page: Int,
  /** Only goods holding such an essence, socketed or in a gem. Null: goods with or without one. */
  val essence: EssenceFilter? = null,
) {

  /** An essence of one of [bestiaIds] (empty: any species) in a [tier] gem (null: any gem). */
  data class EssenceFilter(val bestiaIds: List<Long>, val tier: GemTier?)
}
