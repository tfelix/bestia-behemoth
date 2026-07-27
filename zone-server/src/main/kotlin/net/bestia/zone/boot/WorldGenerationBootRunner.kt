package net.bestia.zone.boot

import net.bestia.zone.world.WorldService
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Detects whether a world exists and generates it if not.
 *
 * First of the boot runners, because the world is the thing everything else stands on: entities are loaded at
 * positions in it, and mobs are spawned onto its terrain. It is also the slowest step by some margin, and
 * failing it early beats importing every item and skill first and only then discovering the world cannot be
 * generated.
 *
 * Not to be confused with [WorldBootRunner], which starts the ECS tick loop as the *last* step.
 */
@Component
@Order(1)
class WorldGenerationBootRunner(
  private val worldService: WorldService
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    worldService.load()
  }
}
