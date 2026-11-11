package net.bestia.bus.contract

/** Whole bestia orders, as a preset holds them. The fields mean what they mean on [ZoneQuery.SetOrders]. */
data class OrderSheet(
  val job: String,
  val courage: String,
  val fallbackJob: String? = null,
  val overrides: OrderNumbers = OrderNumbers(),
  val rules: List<OrderRuleLine> = emptyList(),
  val areaPlaceId: Long? = null,
  val chestPlaceId: Long? = null,
)
