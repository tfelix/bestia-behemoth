package net.bestia.zone.item.container

import net.bestia.zone.BestiaException

/**
 * The reserved items were not what settlement expected them to be, so the exchange was rolled back whole.
 *
 * Thrown rather than returned because throwing is what rolls the transaction back, and a half-moved trade is
 * the one outcome that must be impossible. Reaching this means something moved a promised slot behind the
 * trade's back, which no honest path can do - it is a bug report, not a player-facing refusal.
 */
class TradeSettlementFailedException(
  tradeId: Long,
  detail: String,
) : BestiaException(
  code = "TRADE_SETTLEMENT_FAILED",
  message = "Trade $tradeId could not be settled: $detail"
)
