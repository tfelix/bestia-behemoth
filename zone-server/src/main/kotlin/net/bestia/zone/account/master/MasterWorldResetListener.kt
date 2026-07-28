package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.world.WorldRecreatedEvent
import net.bestia.zone.world.WorldService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Puts every master back at the default spawn after the world has been thrown away and rebuilt.
 *
 * A stored position is a coordinate into terrain that no longer exists. Left alone it does not fail loudly -
 * the player logs in and is simply somewhere wrong: inside a hill, at the bottom of the sea, or in the drowned
 * margin around the edge - and the streaming layer happily sends them the terrain they are buried in. So the
 * reset is part of the regeneration rather than something to notice later.
 *
 * Here rather than in `WorldService` because a master is not the world module's to know about, and because the
 * dependency only points this way: `MasterFactory` already asks [WorldService] where to put a new master, and
 * this asks the same question for the existing ones.
 */
@Component
class MasterWorldResetListener(
  private val masterRepository: MasterRepository,
  private val worldService: WorldService
) {

  /**
   * Loads every master at once, which is fine at the scale this runs at and would not be at another.
   *
   * It happens once, at boot, only under `worldgen.on-mismatch: REGENERATE`, and only when the world was
   * actually discarded - so the alternative, a paged update, would be complexity bought for a path that a
   * production server never takes.
   */
  @EventListener
  @Transactional
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    val spawn = worldService.defaultSpawn
    val masters = masterRepository.findAll()

    if (masters.isEmpty()) return

    for (master in masters) {
      master.spawnPosition = spawn
      master.currentPosition = spawn
    }

    masterRepository.saveAll(masters)

    LOG.warn {
      "World '${event.world.name}' was regenerated; moved ${masters.size} masters to the new spawn $spawn, " +
          "because where they were standing is not there any more"
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
