package net.bestia.zone.settlement

import net.bestia.zone.ecs.core.World
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Loads the player towns and raises the sign of each standing one. A sign is not a stored entity, so every boot
 * raises it again. After the ward stones (`@Order(112)`), with the other always-present entities.
 */
@Component
@Order(113)
class TownBootRunner(
  private val registry: TownRegistry,
  private val towns: TownService,
  private val world: World,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    registry.loadAll()
    towns.resolveSkills()
    towns.regrow(world)

    registry.all().filter { it.isStanding }.forEach { towns.raiseSign(world, it) }
  }
}
