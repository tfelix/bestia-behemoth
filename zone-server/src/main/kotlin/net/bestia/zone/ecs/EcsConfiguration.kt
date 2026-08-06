package net.bestia.zone.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.EntityIdGenerator
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
    systems: List<System>,
    worldConfig: WorldConfig,
    idGenerator: EntityIdGenerator,
  ): World {
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
