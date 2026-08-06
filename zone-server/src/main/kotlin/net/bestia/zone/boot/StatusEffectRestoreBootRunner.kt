package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.persistence.StatusEffectPersistenceService
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Re-attaches persisted status effects to the entities [EntityLoaderBootRunner] just rehydrated.
 *
 * A separate pass, ordered immediately after that runner (110), rather than something each
 * [net.bestia.zone.ecs.persistence.EntityPersister.loadAll] has to remember to do: effects are stored
 * per entity id and are indifferent to entity kind, so one sweep covers mobs, ground items and
 * script entities alike.
 *
 * Player masters are absent here by design - they are materialized on login, and
 * [net.bestia.zone.account.master.MasterEntitySpawner] restores their effects at that point.
 */
@Component
@Order(111)
class StatusEffectRestoreBootRunner(
  private val world: World,
  private val statusEffectPersistenceService: StatusEffectPersistenceService,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    val stored = statusEffectPersistenceService.loadAll()
    if (stored.isEmpty()) {
      return
    }

    var restored = 0
    for ((entityId, effects) in stored) {
      // Rows outlive their entity in one case that is not an error: a master that has never been
      // selected, seeded at creation. Those get attached by MasterEntitySpawner instead.
      world.modify(entityId) { id ->
        statusEffectPersistenceService.attach(this, id, effects)
        restored++
      }
    }

    LOG.info { "Restored status effects onto $restored of ${stored.size} entity/entities holding stored effects." }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
