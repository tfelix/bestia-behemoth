package net.bestia.zone.boot

import net.bestia.zone.ecs.core.World
import net.bestia.zone.faction.envoy.Envoys
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Raises the Envoys of the Orders at every town's temple. With the other NPCs that are not stored. */
@Component
@Order(114)
class EnvoyBootRunner(
  private val envoys: Envoys,
  private val world: World,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    envoys.raiseAll(world)
  }
}
