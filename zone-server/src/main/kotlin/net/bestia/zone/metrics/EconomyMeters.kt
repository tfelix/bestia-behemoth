package net.bestia.zone.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.economy.WorldMoneySupply
import net.bestia.zone.economy.WorldReserve
import org.springframework.stereotype.Component

/**
 * Where the world's coin is, read when scraped. `GameMetrics` counts coin moving; this says how much each
 * holder has, which is what shows a town hoarding or the reserve running dry.
 *
 * The players' share is not counted from their bags, which would mean reading every inventory. It is what the
 * fixed supply leaves after the reserve and the settlements.
 */
@Component
class EconomyMeters(
  private val supply: WorldMoneySupply,
  private val reserve: WorldReserve,
  private val settlements: SettlementEconomyService,
) : MeterBinder {

  override fun bindTo(registry: MeterRegistry) {
    held(registry, "reserve") { reserve.available() }
    held(registry, "settlements") { settlementsHold() }
    held(registry, "players") { supply.npcHoldings + supply.unminted - reserve.available() - settlementsHold() }

    Gauge.builder("zone.economy.settlements.disturbed", settlements) { it.disturbedSettlements.toDouble() }
      .description("Settlements whose stock, price or treasury is away from their reference")
      .register(registry)
  }

  private fun settlementsHold(): Double {
    return supply.npcHoldings + settlements.treasurySurplus
  }

  private fun held(registry: MeterRegistry, holder: String, value: () -> Double) {
    Gauge.builder("zone.economy.coin.held") { value() }
      .tag("holder", holder)
      .description("Coin held by the world reserve, the settlement treasuries, and the players (the rest of the supply)")
      .register(registry)
  }
}
