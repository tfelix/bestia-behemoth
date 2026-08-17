package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.spawn.DenPackRestoreService
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Puts the creatures [EntityLoaderBootRunner] has just rehydrated back under the den that made them.
 *
 * A pass of its own, for the reason [StatusEffectRestoreBootRunner] is one: this rebuilds **cross-entity**
 * state - a link from a creature to a den - and neither end's own `loadAll` is the place for it.
 * `MobEntityPersister` carries the den reference and deliberately never interprets it; the interpreting
 * happens here.
 *
 * ### `@Order(112)` is the only window that works
 *
 *  - after [WildSpawnerBootRunner] (105), or there is no den to look up;
 *  - after [EntityLoaderBootRunner] (110), or there is no creature to attach;
 *  - after [StatusEffectRestoreBootRunner] (111), only to keep the entity-restore passes in one block;
 *  - **before** `WorldBootRunner` starts the tick loop and `SocketServerBootRunner` opens the socket.
 *
 * That last one is load bearing twice over. It is what makes it safe for `DenPackRestoreService` to touch
 * `SpawnerSystem`'s plain `HashSet` and `Spawner.spawnedEntities` from this thread with no tick in flight,
 * and it is what confines an orphaned creature's brief existence in the world - it is spawned at 110 and
 * destroyed here - to a window nobody can observe. Reordering these runners, or giving
 * `MobEntityPersister.loadAll` a second caller, breaks both properties silently.
 */
@Component
@Order(112)
class DenPackRestoreBootRunner(
  private val world: World,
  private val denPackRestoreService: DenPackRestoreService,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    val result = denPackRestoreService.restore(world)

    LOG.info {
      "Den packs restored: ${result.reattached} creature(s) handed back to ${result.adoptedDens} den(s)"
    }

    if (result.trimmed > 0) {
      LOG.warn { "${result.trimmed} restored creature(s) exceeded their den's pack size and were discarded" }
    }

    if (result.unowned > 0) {
      LOG.warn {
        "${result.unowned} persisted creature(s) belong to no den - `/spawn`ed, or written before dens " +
            "owned their packs. Nothing despawns these; they will stay until they are killed."
      }
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
