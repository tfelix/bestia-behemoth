package net.bestia.zone.trade

/**
 * Why a trade stopped. Decides which of the `TRADE_*` [net.bestia.bnet.proto.OperationErrorProto.OpError]
 * codes each side is told, and whether they are told anything at all - the player who pressed Cancel does not
 * need to be informed that they pressed Cancel.
 */
enum class TradeEndReason {
  /** The invitation was never answered. Only the requester hears about it. */
  EXPIRED,

  /** Answered with a no. Only the requester hears about it. */
  DECLINED,

  /** Somebody pressed Cancel, or logged out. The other side hears about it, and is told who. */
  CANCELLED,

  /** The two walked more than ten tiles apart. Both are told, since neither chose it. */
  WALKED_AWAY,

  /** One of the entities stopped existing - despawned, or logged out far enough for its entity to go. */
  PARTNER_GONE,

  /** The exchange could not be committed and was rolled back whole. Both are told; nothing changed hands. */
  FAILED,
}
