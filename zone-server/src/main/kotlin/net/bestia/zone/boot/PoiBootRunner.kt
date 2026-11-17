package net.bestia.zone.boot

import net.bestia.zone.ecs.core.World
import net.bestia.zone.poi.PoiService
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Raises the world's POIs. After [StatusEffectImporterBootRunner] (`@Order(104)`), because a POI carries
 * its effects from the moment it stands.
 */
@Component
@Order(112)
class PoiBootRunner(
  private val pois: PoiService,
  private val world: World,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    pois.raise(world)
  }
}
