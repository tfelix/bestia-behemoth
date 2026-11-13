package net.bestia.zone.boot

import net.bestia.zone.ecs.core.World
import net.bestia.zone.worldboss.WorldBossLairService
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Places a den at every lair whose world boss still lives. After the wild dens, for the same reasons as
 * [WildSpawnerBootRunner], and before [EntityLoaderBootRunner] (`@Order(110)`).
 */
@Component
@Order(107)
class WorldBossLairBootRunner(
  private val lairs: WorldBossLairService,
  private val world: World,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    lairs.placeDens(world)
  }
}
