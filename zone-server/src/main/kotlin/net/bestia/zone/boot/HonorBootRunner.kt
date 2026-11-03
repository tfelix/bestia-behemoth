package net.bestia.zone.boot

import net.bestia.zone.honor.LocalHonorRegistry
import net.bestia.zone.townsfolk.ecs.TownsfolkDeaths
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Loads what towns think of each Master, and who of them was killed. Beside [RumourBootRunner], after the world
 * record exists.
 */
@Component
@Order(7)
class HonorBootRunner(
  private val registry: LocalHonorRegistry,
  private val townsfolkDeaths: TownsfolkDeaths,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    registry.loadAll()
    townsfolkDeaths.loadAll()
  }
}
