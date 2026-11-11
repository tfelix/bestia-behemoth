package net.bestia.zone.companion.query

import net.bestia.zone.ai.profile.OrderRule
import net.bestia.bus.contract.OrderRuleLine
import net.bestia.bus.contract.OrderNumbers
import net.bestia.zone.ai.profile.OrderOverrides

/** Bestia orders to and from the bus the companion app talks over. */
object OrdersOnTheBus {

  fun numbersOf(overrides: OrderOverrides) = OrderNumbers(
    retreatBelowHealthPct = overrides.retreatBelowHealthPct,
    eatBelowStaminaPct = overrides.eatBelowStaminaPct,
    healBelowHealthPct = overrides.healBelowHealthPct,
    haulAtLoadPct = overrides.haulAtLoadPct,
    areaRadius = overrides.areaRadius,
  )

  fun linesOf(rules: List<OrderRule>) = rules.map { OrderRuleLine(it.condition.name, it.threshold, it.action.name) }

  /** Null when a line names a condition or action this server does not know. */
  fun rulesOf(lines: List<OrderRuleLine>): List<OrderRule>? {
    return lines.map { OrderRule.fromNamesOrNull(it.condition, it.threshold, it.action) ?: return null }
  }

  fun overridesOf(numbers: OrderNumbers) = OrderOverrides(
    retreatBelowHealthPct = numbers.retreatBelowHealthPct,
    eatBelowStaminaPct = numbers.eatBelowStaminaPct,
    healBelowHealthPct = numbers.healBelowHealthPct,
    haulAtLoadPct = numbers.haulAtLoadPct,
    areaRadius = numbers.areaRadius,
  )
}
