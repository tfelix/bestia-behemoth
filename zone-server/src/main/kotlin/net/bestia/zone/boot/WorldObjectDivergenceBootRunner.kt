package net.bestia.zone.boot

import net.bestia.zone.world.WorldService
import net.bestia.zone.world.prop.WorldObjectDivergenceRegistry
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Loads which generated static entities (trees, POI landmarks, ...) have already been depleted, so
 * [net.bestia.zone.world.prop.WorldObjectResidencyService] never resurrects one a player felled or claimed
 * on a prior run.
 *
 * Ordered right after [WorldGenerationBootRunner] (`@Order(1)`) so [WorldService.record] - and therefore both
 * of the registry's orphan-guard versions - is available, and grouped with "things about the world" alongside
 * [SettlementSpawnPointBootRunner] (`@Order(2)`) rather than "things about entities", well before
 * [EntityLoaderBootRunner] (`@Order(110)`).
 *
 * The versions are read by the registry rather than passed from here, so this stays a one-line "go" and cannot
 * be the place a mismatched pair is assembled - see [WorldObjectDivergenceRegistry]'s constructor.
 */
@Component
@Order(3)
class WorldObjectDivergenceBootRunner(
  private val registry: WorldObjectDivergenceRegistry,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    registry.loadAll()
  }
}
