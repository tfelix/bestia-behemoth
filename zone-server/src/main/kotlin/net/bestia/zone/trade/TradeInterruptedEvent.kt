package net.bestia.zone.trade

import net.bestia.zone.BestiaEvent

/**
 * The world has ended a trade: the two walked apart, or one of their entities stopped existing.
 *
 * An event rather than a direct call, because the only thing that can notice is a `System` and systems are
 * collected into the ECS world - so a system holding a [TradeService] that holds a `WorldView` would be a
 * construction cycle. It also keeps the tick thread out of the trade internals: the listener does nothing but
 * hand the cleanup to a worker.
 */
class TradeInterruptedEvent(
  source: Any,
  val tradeId: Long,
  val reason: TradeEndReason,
) : BestiaEvent(source)
