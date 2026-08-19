package net.bestia.zone.trade

/**
 * Where a trade has got to. The wire only ever sees [OPEN], [LOCKED], and the two terminal states via
 * `TradeStateSMSG`; [PENDING] and [SETTLING] are server-side moments with no window to draw.
 */
enum class TradeStatus {
  /** Asked, not yet answered. */
  PENDING,

  /** Both windows open; either side may add and retract. */
  OPEN,

  /** Both sides locked. Contents frozen, waiting on the two confirmations. */
  LOCKED,

  /** Both confirmed and the exchange is being committed. Refuses every message, including Cancel. */
  SETTLING,

  /** Over, one way or the other. */
  CLOSED,
}
