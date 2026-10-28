package net.bestia.zone.economy.shop

import net.bestia.zone.util.EntityId

/** One merchant's shop: who keeps it, what they deal in, and when. */
class Counter(
  val merchantId: EntityId,
  val merchantName: String,
  /** Commodity ids; the town may hold plenty the counter has never had. */
  val stocked: Set<String>,
  val hours: ShopHours,
)
