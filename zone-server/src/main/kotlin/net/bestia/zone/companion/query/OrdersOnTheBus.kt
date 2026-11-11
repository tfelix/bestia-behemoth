package net.bestia.zone.companion.query

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

  fun overridesOf(numbers: OrderNumbers) = OrderOverrides(
    retreatBelowHealthPct = numbers.retreatBelowHealthPct,
    eatBelowStaminaPct = numbers.eatBelowStaminaPct,
    healBelowHealthPct = numbers.healBelowHealthPct,
    haulAtLoadPct = numbers.haulAtLoadPct,
    areaRadius = numbers.areaRadius,
  )
}
