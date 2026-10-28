package net.bestia.zone.economy.shop

import net.bestia.bnet.proto.ShopTradeCmsgProto
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.Shop

/** One line of a shop basket, as the player asked for it. */
data class ShopTradeLine(val itemId: Long, val amount: Int, val selling: Boolean) {

  companion object {
    /**
     * Cap on one line, and the reason is arithmetic rather than balance: every unit is priced separately
     * against a town that has one fewer of them, so an unbounded amount is an unbounded loop on the tick
     * thread. Carrying capacity bounds a real trip well below this anyway.
     */
    const val MAX_UNITS = 500

    /** Bounds the same loop across a whole basket. */
    const val MAX_LINES = 16

    /** A line in something [counter] does not deal in keeps a null commodity, which the quote refuses. */
    fun basketOf(lines: List<ShopTradeLine>, counter: Counter, commodities: CommodityItems): List<Shop.BasketLine> {
      return lines.map { line ->
        val commodity = commodities.commodityOf(line.itemId)?.takeIf { it.id in counter.stocked }
        Shop.BasketLine(commodity, line.amount, line.selling)
      }
    }

    fun fromBnet(lines: List<ShopTradeCmsgProto.ShopLine>): List<ShopTradeLine> {
      return lines.map { ShopTradeLine(it.itemId, it.amount, it.selling) }
    }

    /**
     * An honest client never sends anything else, so a basket that is not is dropped without a reply. Each
     * item may appear once only: buying and selling the same good in one trade is a round trip the spread
     * only makes a loss of.
     */
    fun isWellFormed(lines: List<ShopTradeLine>): Boolean {
      return lines.size in 1..MAX_LINES &&
        lines.all { it.amount in 1..MAX_UNITS } &&
        lines.distinctBy { it.itemId }.size == lines.size
    }
  }
}
