package net.bestia.zone.boot

import net.bestia.zone.ecs.core.World
import net.bestia.zone.spawn.ecs.BattlefieldHaunts
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Places the night dens on battlefields, beside the wild dens, the lairs and the relic guards. */
@Component
@Order(108)
class BattlefieldHauntBootRunner(
  private val haunts: BattlefieldHaunts,
  private val world: World,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    haunts.placeDens(world)
  }
}
