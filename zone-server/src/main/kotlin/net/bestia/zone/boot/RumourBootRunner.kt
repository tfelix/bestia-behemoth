package net.bestia.zone.boot

import net.bestia.zone.ai.rumour.RumourRegistry
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Loads what each town had heard, so news a player made survives a restart.
 *
 * `@Order(7)`, at the end of the "things about the world" group - after [WorldGenerationBootRunner]
 * (`@Order(1)`) so the version guards have a record to compare against, and beside [ScorchBootRunner]
 * rather than with the entity loaders at `@Order(110)`.
 *
 * The versions are read by the registry itself, so this stays a one-line "go" and cannot be the place a
 * mismatched pair is assembled.
 */
@Component
@Order(7)
class RumourBootRunner(
  private val registry: RumourRegistry,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    registry.loadAll()
  }
}
