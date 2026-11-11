package net.bestia.zone.companion.query

import net.bestia.bus.contract.OrderNumbers
import net.bestia.bus.contract.OrderRuleLine
import net.bestia.bus.contract.OrderSheet
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.ai.profile.Courage
import net.bestia.zone.ai.profile.OrderOverrides
import net.bestia.zone.ai.profile.OrderRule
import net.bestia.zone.master.bestia.StoredOrders

/** Bestia orders to and from the bus the companion app talks over. */
object OrdersOnTheBus {

  /** Orders that can be followed, or a failure that says what is wrong with [sheet]. */
  fun ordersOf(sheet: OrderSheet): Result<AiConfig> {
    val job = BestiaJob.fromNameOrNull(sheet.job) ?: return invalid("Unknown job '${sheet.job}'")
    val courage = Courage.fromNameOrNull(sheet.courage) ?: return invalid("Unknown courage '${sheet.courage}'")
    val fallbackJob = sheet.fallbackJob?.let {
      BestiaJob.fromNameOrNull(it) ?: return invalid("Unknown fallback job '$it'")
    }
    val rules = sheet.rules.map {
      OrderRule.fromNamesOrNull(it.condition, it.threshold, it.action) ?: return invalid("Unknown rule $it")
    }
    val orders = AiConfig(job, courage, fallbackJob, overridesOf(sheet.overrides), rules)

    return if (orders.isValid) Result.success(orders) else invalid("Orders that cannot be followed: $orders")
  }

  fun sheetOf(orders: AiConfig) = OrderSheet(
    job = orders.job.name,
    courage = orders.courage.name,
    fallbackJob = orders.fallbackJob?.name,
    overrides = numbersOf(orders.overrides),
    rules = linesOf(orders.rules),
  )

  fun answerOf(playerBestiaId: Long, stored: StoredOrders): ZoneAnswer.Orders {
    val sheet = sheetOf(stored.orders)
    return ZoneAnswer.Orders(
      playerBestiaId, sheet.job, sheet.courage, sheet.fallbackJob, sheet.overrides, sheet.rules,
      stored.presetId, numbersOf(stored.ownOverrides),
    )
  }

  fun numbersOf(overrides: OrderOverrides) = OrderNumbers(
    retreatBelowHealthPct = overrides.retreatBelowHealthPct,
    eatBelowStaminaPct = overrides.eatBelowStaminaPct,
    healBelowHealthPct = overrides.healBelowHealthPct,
    haulAtLoadPct = overrides.haulAtLoadPct,
    areaRadius = overrides.areaRadius,
  )

  fun linesOf(rules: List<OrderRule>) = rules.map { OrderRuleLine(it.condition.name, it.threshold, it.action.name) }

  private fun overridesOf(numbers: OrderNumbers) = OrderOverrides(
    retreatBelowHealthPct = numbers.retreatBelowHealthPct,
    eatBelowStaminaPct = numbers.eatBelowStaminaPct,
    healBelowHealthPct = numbers.healBelowHealthPct,
    haulAtLoadPct = numbers.haulAtLoadPct,
    areaRadius = numbers.areaRadius,
  )

  private fun invalid(why: String): Result<AiConfig> = Result.failure(IllegalArgumentException(why))
}
