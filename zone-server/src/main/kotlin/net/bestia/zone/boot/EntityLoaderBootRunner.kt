package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.persistence.EntityPersister
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import net.bestia.zone.util.inClassNameOrder

/**
 * Rehydrates persisted world entities (mobs, ground items, ...) into the ECS world at startup.
 *
 * Ordered after the template importers (item/mob/skill defs, order ~100-104) so the entity spawners can
 * re-derive static stats, and before [SocketServerBootRunner] ([org.springframework.core.Ordered.LOWEST_PRECEDENCE])
 * so no client can connect while the world is still being populated. Player masters/bestias are
 * loaded on login instead and are skipped here (their persisters report `loadsAtStartup = false`).
 */
@Component
@Order(110)
class EntityLoaderBootRunner(
  private val world: EcsWorld,
  foundPersisters: List<EntityPersister>,
) : CommandLineRunner {

  private val persisters = foundPersisters.inClassNameOrder()

  override fun run(vararg args: String?) {
    val startupPersisters = persisters.filter { it.loadsAtStartup }
    LOG.info { "Loading persisted entities via ${startupPersisters.size} persister(s)..." }

    startupPersisters.forEach { it.loadAll(world) }

    LOG.info { "Finished loading persisted entities; world now holds ${world.entityCount} entity/entities." }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
