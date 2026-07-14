package net.bestia.zone.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.SnowflakeEntityIdGenerator
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ZoneConfig as ZoneShardConfig
import net.bestia.zone.ecs.ZoneConfig as WorldConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring wiring for the ecs [net.bestia.zone.ecs.core.World]. Collects every [net.bestia.zone.ecs.core.System] bean and
 * registers it into a single [net.bestia.zone.ecs.core.World] — the same `List<T>` bean-collection
 * mechanism the existing `ZoneServer` uses for its systems.
 *
 * This deliberately does NOT start a tick loop; wire an [EcsRunner] (or drive
 * [net.bestia.zone.ecs.core.World.tick] yourself) when you want it to actually run.
 */
@Configuration
class EcsConfiguration {

  @Bean
  fun ecsWorld(
    systems: List<System>,
    worldConfig: WorldConfig,
    zoneShardConfig: ZoneShardConfig,
  ): World {
    val idGenerator = SnowflakeEntityIdGenerator(nodeId = zoneShardConfig.shardId.coerceIn(0, 255))
    val world = World(
      parallelSystems = worldConfig.parallelSystems,
      idGenerator = idGenerator,
      systems = systems
    )

    LOG.info {
      "ECS initialised (parallel=${worldConfig.parallelSystems}) with ${systems.size} system(s) " +
        "across ${world.waveCount} wave(s):\n" +
        systems.joinToString("\n") { " - ${it.name} [${it.schedule}]" }
    }
    return world
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
