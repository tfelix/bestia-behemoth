package net.bestia.zone.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.EntityIdGenerator
import net.bestia.zone.ecs.core.SnowflakeEntityIdGenerator
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ZoneConfig as ZoneShardConfig
import net.bestia.zone.ecs.ZoneConfig as WorldConfig
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring wiring for the ecs [EcsWorld]. The World is built empty and gets every [System] bean only once all
 * singletons exist, so a service a system depends on can still inject the World. `ZoneEngine` runs the tick.
 */
@Configuration
class EcsConfiguration {

  /**
   * The one id source for the whole zone. Exposed as a bean rather than kept inside [ecsWorld]
   * because ids are also handed out *before* an entity exists — [net.bestia.zone.account.master.MasterFactory]
   * stamps a master's [net.bestia.zone.util.EntityId] at creation so persisted per-entity state can be
   * written for it long before it is ever spawned. A second generator instance would defeat the
   * snowflake's uniqueness, since two of them with the same node id emit the same timestamp|node|sequence.
   */
  @Bean
  fun entityIdGenerator(zoneShardConfig: ZoneShardConfig): EntityIdGenerator =
    SnowflakeEntityIdGenerator(nodeId = zoneShardConfig.shardId.coerceIn(0, 255))

  @Bean
  fun ecsWorld(
    worldConfig: WorldConfig,
    idGenerator: EntityIdGenerator,
  ): EcsWorld {
    return EcsWorld(
      parallelSystems = worldConfig.parallelSystems,
      idGenerator = idGenerator,
      undeclaredAccess = worldConfig.undeclaredAccess,
    )
  }

  @Bean
  fun systemRegistration(
    world: EcsWorld,
    systems: ObjectProvider<System>,
    worldConfig: WorldConfig,
  ): SmartInitializingSingleton {
    return SmartInitializingSingleton {
      world.registerSystems(systems.stream().toList())

      LOG.info {
        "ECS initialised (parallel=${worldConfig.parallelSystems}) with ${world.systemCount} system(s) " +
          "across ${world.waveCount} wave(s):\n" + world.describeSystems()
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
