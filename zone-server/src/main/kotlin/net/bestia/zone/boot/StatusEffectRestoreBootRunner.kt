package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.ecs.core.World
import net.bestia.zone.battle.persistence.StatusEffectPersistenceService
import net.bestia.zone.util.EntityId
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import net.bestia.zone.ecs.core.modify

/**
 * Re-attaches persisted status effects to the entities [EntityLoaderBootRunner] just rehydrated, and drops the
 * rows that belong to nobody.
 *
 * A separate pass, ordered immediately after that runner (110), rather than something each
 * [net.bestia.zone.persistence.EntityPersister.loadAll] has to remember to do: effects are stored
 * per entity id and are indifferent to entity kind, so one sweep covers mobs, ground items and
 * script entities alike.
 *
 * Masters and their bestias are absent here by design - they are materialized on login, and
 * [net.bestia.zone.master.MasterEntitySpawner] and [net.bestia.zone.master.bestia.PlayerBestiaEntitySpawner]
 * restore their effects at that point.
 */
@Component
@Order(111)
class StatusEffectRestoreBootRunner(
  private val world: World,
  private val statusEffectPersistenceService: StatusEffectPersistenceService,
  private val masterRepository: MasterRepository,
  private val playerBestiaRepository: PlayerBestiaRepository,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    restoreStoredEffects()
    dropOrphanedEffects()
  }

  private fun restoreStoredEffects() {
    val stored = statusEffectPersistenceService.loadAll()
    if (stored.isEmpty()) {
      return
    }

    var restored = 0
    for ((entityId, effects) in stored) {
      // An entity that is not in the world yet is normal for a master or bestia waiting for its owner's login.
      world.modify(entityId) { id ->
        statusEffectPersistenceService.attach(this, id, effects)
        restored++
      }
    }

    LOG.info { "Restored status effects onto $restored of ${stored.size} entity/entities holding stored effects." }
  }

  /** Rows whose owner neither came back above nor waits for a login would never be read again. */
  private fun dropOrphanedEffects() {
    val absent = statusEffectPersistenceService.storedOwners().filterNot { world.isAlive(it) }
    if (absent.isEmpty()) {
      return
    }

    val waitingForLogin = masterRepository.findEntityIdsIn(absent) + playerBestiaRepository.findEntityIdsIn(absent)
    val orphans: List<EntityId> = absent - waitingForLogin.toSet()
    statusEffectPersistenceService.deleteFor(orphans)

    if (orphans.isNotEmpty()) {
      LOG.info { "Dropped the stored status effects of ${orphans.size} entity/entities that no longer exist." }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
