package net.bestia.zone.economy.shop

/** What the economy needs to know about a townsperson to put them behind a counter. */
class Merchant(
  val name: String,
  /** A `BusinessType.id`, or null for somebody who keeps no trade. */
  val business: String?,
  val hours: ShopHours,
)
