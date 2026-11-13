package net.bestia.zone.boot

import net.bestia.zone.ecs.core.World
import net.bestia.zone.treasure.RelicGuards
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Places the guards of artifact tombs and monuments, beside the wild dens and the lairs. */
@Component
@Order(108)
class RelicGuardBootRunner(
  private val guards: RelicGuards,
  private val world: World,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    guards.placeDens(world)
  }
}
