package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.persistence.EntityPersister
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Rehydrates persisted world entities (mobs, ground items, ...) into the ECS world at startup.
 *
 * Ordered after the template importers (item/mob/skill defs, order ~100-104) so factories can
 * re-derive static stats, and before [SocketServerBootRunner] ([org.springframework.core.Ordered.LOWEST_PRECEDENCE])
 * so no client can connect while the world is still being populated. Player masters/bestias are
 * loaded on login instead and are skipped here (their persisters report `loadsAtStartup = false`).
 */
@Component
@Order(110)
class EntityLoaderBootRunner(
  private val world: World,
  private val persisters: List<EntityPersister>,
) : CommandLineRunner {

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
