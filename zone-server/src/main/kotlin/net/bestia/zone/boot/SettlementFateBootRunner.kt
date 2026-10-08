package net.bestia.zone.boot

import net.bestia.zone.prop.SettlementFateService
import net.bestia.zone.world.SpawnPointAvailability
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Finds the settlements that fell before this run, from the divergence rows [WorldObjectDivergenceBootRunner]
 * (`@Order(3)`) loads.
 */
@Component
@Order(10)
class SettlementFateBootRunner(
  private val fates: SettlementFateService,
  private val availability: SpawnPointAvailability,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    fates.deriveAll()
    // The watch decides the offers first after thirty seconds. Until then a fallen town would still be offered.
    availability.refresh()
  }
}
