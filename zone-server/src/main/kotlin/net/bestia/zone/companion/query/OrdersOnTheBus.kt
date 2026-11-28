package net.bestia.zone.companion.query

import net.bestia.bus.contract.OrderCareItems
import net.bestia.bus.contract.OrderGatherFilter
import net.bestia.bus.contract.OrderNumbers
import net.bestia.bus.contract.OrderRuleLine
import net.bestia.bus.contract.OrderSheet
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.ai.profile.Courage
import net.bestia.zone.ai.profile.GatherFilter
import net.bestia.zone.ai.profile.OrderOverrides
import net.bestia.zone.ai.profile.OrderRule
import net.bestia.zone.item.care.CareItems
import net.bestia.zone.prop.GatherCategory
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
    val careItems = CareItems(sheet.careItems.neverUse, sheet.careItems.alsoUse)
    val skip = sheet.gatherFilter.skip.map { name ->
      GatherCategory.entries.find { it.name == name } ?: return invalid("Unknown gather category '$name'")
    }
    val gatherFilter = GatherFilter(skip.toSet(), sheet.gatherFilter.onlyItems)
    val orders = AiConfig(
      job, courage, fallbackJob, overridesOf(sheet.overrides), rules, sheet.areaPlaceId, sheet.chestPlaceId, careItems,
      gatherFilter,
    )

    return if (orders.isValid) Result.success(orders) else invalid("Orders that cannot be followed: $orders")
  }

  fun sheetOf(orders: AiConfig) = OrderSheet(
    job = orders.job.name,
    courage = orders.courage.name,
    fallbackJob = orders.fallbackJob?.name,
    overrides = numbersOf(orders.overrides),
    rules = linesOf(orders.rules),
    areaPlaceId = orders.areaPlaceId,
    chestPlaceId = orders.chestPlaceId,
    careItems = careItemsOf(orders.careItems),
    gatherFilter = gatherFilterOf(orders.gatherFilter),
  )

  fun answerOf(playerBestiaId: Long, stored: StoredOrders): ZoneAnswer.Orders {
    val sheet = sheetOf(stored.orders)
    return ZoneAnswer.Orders(
      playerBestiaId, sheet.job, sheet.courage, sheet.fallbackJob, sheet.overrides, sheet.rules,
      sheet.areaPlaceId, sheet.chestPlaceId, stored.presetId, numbersOf(stored.ownOverrides), sheet.careItems,
      sheet.gatherFilter,
    )
  }

  fun numbersOf(overrides: OrderOverrides) = OrderNumbers(
    retreatBelowHealthPct = overrides.retreatBelowHealthPct,
    eatBelowStaminaPct = overrides.eatBelowStaminaPct,
    healBelowHealthPct = overrides.healBelowHealthPct,
    haulAtLoadPct = overrides.haulAtLoadPct,
    areaRadius = overrides.areaRadius,
  )

  fun careItemsOf(items: CareItems): OrderCareItems {
    return OrderCareItems(neverUse = items.neverUse, alsoUse = items.alsoUse)
  }

  fun gatherFilterOf(filter: GatherFilter): OrderGatherFilter {
    return OrderGatherFilter(skip = filter.skip.sorted().map { it.name }, onlyItems = filter.onlyItems)
  }

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
