package net.bestia.zone.ecs2.spring

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs2.Ecs2System
import net.bestia.zone.ecs2.World
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring wiring for the ecs2 namespace. Collects every [Ecs2System] bean and
 * registers it into a single [World] — the same `List<T>` bean-collection
 * mechanism the existing `ZoneServer` uses for its systems.
 *
 * This deliberately does NOT start a tick loop; ecs2 lives alongside the current
 * ECS without taking over. Wire an [Ecs2Runner] (or drive [World.tick] yourself)
 * when you want it to actually run.
 */
@Configuration
class Ecs2Configuration {

  @Bean
  fun ecs2World(
    systems: List<Ecs2System>,
    @Value("\${ecs2.parallel-systems:false}") parallelSystems: Boolean,
  ): World {
    val world = World(parallelSystems = parallelSystems)
    world.addSystems(systems)
    LOG.info {
      "ecs2 World initialised (parallel=$parallelSystems) with ${systems.size} system(s) " +
        "across ${world.waveCount} wave(s):\n" +
        systems.joinToString("\n") { " - ${it.name} [${it.schedule}]" }
    }
    return world
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
