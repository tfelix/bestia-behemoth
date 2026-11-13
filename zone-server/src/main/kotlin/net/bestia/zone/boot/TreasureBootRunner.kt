package net.bestia.zone.boot

import net.bestia.zone.treasure.Relics
import net.bestia.zone.treasure.TreasureCacheRegistry
import net.bestia.zone.treasure.TreasureMapRegistry
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Loads which treasure caches were dug, the circles of treasure maps and which copies are relics, so the tick
 * never asks the database. After the world is loaded.
 */
@Component
@Order(10)
class TreasureBootRunner(
  private val registry: TreasureCacheRegistry,
  private val maps: TreasureMapRegistry,
  private val relics: Relics,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    registry.restoreAll()
    maps.restoreAll()
    relics.restoreAll()
  }
}
