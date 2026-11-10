package net.bestia.zone.boot

import net.bestia.zone.treasure.DigService
import net.bestia.zone.treasure.TreasureCacheRegistry
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Loads which treasure caches were dug, and the skill digging reads, so the tick never asks the database. After
 * the world is loaded.
 */
@Component
@Order(10)
class TreasureBootRunner(
  private val registry: TreasureCacheRegistry,
  private val digService: DigService,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    registry.restoreAll()
    digService.excavationSkillId
  }
}
