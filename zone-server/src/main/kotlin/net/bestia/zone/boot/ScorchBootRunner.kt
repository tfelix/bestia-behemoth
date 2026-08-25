package net.bestia.zone.boot

import net.bestia.zone.world.WorldService
import net.bestia.zone.world.fire.ScorchRegistry
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Loads which ground is still burnt, so a scar a player made survives a restart.
 *
 * `@Order(5)`, at the end of the "things about the world" group - after [WorldGenerationBootRunner] (`@Order(1)`)
 * so [WorldService.record] exists for the version guards, and beside
 * [WorldObjectDivergenceBootRunner] (`@Order(3)`) and [PlayerStructureBootRunner] (`@Order(4)`) rather than
 * with the entity loaders at `@Order(110)`.
 *
 * The versions are read by the registry itself, so this stays a one-line "go" and cannot be the place a
 * mismatched pair is assembled.
 */
@Component
@Order(5)
class ScorchBootRunner(
  private val registry: ScorchRegistry,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    registry.loadAll()
  }
}
