package net.bestia.zone.boot

import net.bestia.zone.ecs.core.World
import net.bestia.zone.landmark.LandmarkService
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Raises the world's landmarks. After [StatusEffectImporterBootRunner] (`@Order(104)`), because a landmark carries
 * its effects from the moment it stands.
 */
@Component
@Order(112)
class LandmarkBootRunner(
  private val landmarks: LandmarkService,
  private val world: World,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    landmarks.raise(world)
  }
}
