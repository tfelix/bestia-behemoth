package net.bestia.zone.ecs.core.spring

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.SnowflakeEntityIdGenerator
import net.bestia.zone.ecs.core.World
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring wiring for the ecs [World]. Collects every [System] bean and
 * registers it into a single [World] — the same `List<T>` bean-collection
 * mechanism the existing `ZoneServer` uses for its systems.
 *
 * This deliberately does NOT start a tick loop; wire an [Ecs2Runner] (or drive
 * [World.tick] yourself) when you want it to actually run.
 */
@Configuration
class Ecs2Configuration {

  @Bean
  fun ecsWorld(
    systems: List<System>,
    @Value("\${ecs.core.parallel-systems:false}") parallelSystems: Boolean,
    @Value("\${zone.shard-id:1}") shardId: Int,
  ): World {
    val idGenerator = SnowflakeEntityIdGenerator(nodeId = shardId.coerceIn(0, 255))
    // Spring orders the injected list by @Order, giving a deterministic system execution order
    // (single-threaded => execution order == registration order).
    val world = World(parallelSystems = parallelSystems, idGenerator = idGenerator::nextId)
    world.addSystems(systems)
    LOG.info {
      "ecs World initialised (parallel=$parallelSystems) with ${systems.size} system(s) " +
        "across ${world.waveCount} wave(s):\n" +
        systems.joinToString("\n") { " - ${it.name} [${it.schedule}]" }
    }
    return world
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
