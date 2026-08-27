package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.bestia.PlayerBestiaRepository
import net.bestia.zone.world.MasterSpawnPointService
import net.bestia.zone.world.WorldRecreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Re-homes every master onto a spawn point of the new world after the old one has been thrown away and rebuilt.
 *
 * A stored position is a coordinate into terrain that no longer exists. Left alone it does not fail loudly -
 * the player logs in and is simply somewhere wrong: inside a hill, at the bottom of the sea, or in the drowned
 * margin around the edge - and the streaming layer happily sends them the terrain they are buried in. So the
 * reset is part of the regeneration rather than something to notice later.
 *
 * Here rather than in `WorldService` because a master is not the world module's to know about, and because the
 * dependency only points this way: [MasterFactory] already asks [MasterSpawnPointService] where to put a new
 * master, and this asks the same question for the existing ones.
 */
@Component
class MasterWorldResetListener(
  private val masterRepository: MasterRepository,
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val masterSpawnPointService: MasterSpawnPointService
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
    val masters = masterRepository.findAll()

    if (masters.isEmpty()) return

    // `WorldProvisioning.recreate` cleared the cached candidates along with the world row, so this recomputes
    // them against the terrain that now exists.
    val candidates = masterSpawnPointService.ensureComputed()
    val fallback = candidates.firstOrNull()

    if (fallback == null) {
      LOG.error {
        "World '${event.world.name}' was regenerated but offers no spawn point candidates, so ${masters.size} " +
            "masters are left pointing at terrain that no longer exists. They will log in somewhere wrong."
      }
      return
    }

    // A master keeps its home settlement when one of that name is still standing. That is not wishful thinking:
    // a regeneration under a *pinned* seed - the usual reason for one, a pipeline version bump - rebuilds the
    // same world, so the same settlements come back under the same names. A reseed matches nothing and everyone
    // lands on the first candidate.
    val byName = candidates.associateBy { it.settlementName }
    var kept = 0
    var bestias = 0

    for (master in masters) {
      val spawnPoint = byName[master.homeSettlementName]?.also { kept++ } ?: fallback

      master.spawnPosition = spawnPoint.position
      master.currentPosition = spawnPoint.position
      master.homeSettlementName = spawnPoint.settlementName

      // An owned bestia's save point is a stored coordinate into the same discarded terrain, so it
      // needs re-homing for exactly the reason the master's does. Onto the owner's new spawn point
      // rather than the old one it was stationed at, which is not there any more either.
      val ownedBestias = playerBestiaRepository.findAllByMasterId(master.id)
      for (playerBestia in ownedBestias) {
        playerBestia.spawnPosition = spawnPoint.position
        playerBestia.position = spawnPoint.position
      }
      playerBestiaRepository.saveAll(ownedBestias)
      bestias += ownedBestias.size
    }

    masterRepository.saveAll(masters)

    LOG.warn {
      "World '${event.world.name}' was regenerated; re-homed ${masters.size} masters and $bestias owned " +
          "bestias onto its spawn points ($kept masters kept their home settlement, ${masters.size - kept} " +
          "were moved to '${fallback.settlementName}' at ${fallback.position}), because where they were " +
          "standing is not there any more"
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
