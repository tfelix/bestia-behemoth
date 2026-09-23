package net.bestia.zone.boot

import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.world.WorldService
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Loads the books of the settlements a player has disturbed, so a town they emptied is still empty after
 * a restart.
 *
 * `@Order(6)`, beside [ScorchBootRunner] in the "things about the world" group and after
 * [WorldGenerationBootRunner] so [WorldService.record] exists for the version guards.
 */
@Component
@Order(6)
class SettlementEconomyBootRunner(
  private val economy: SettlementEconomyService,
  private val reserve: WorldReserve,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    // The reserve first: it is what the settlements' own catch-up is paid out of.
    reserve.load()
    economy.loadAll()
  }
}
