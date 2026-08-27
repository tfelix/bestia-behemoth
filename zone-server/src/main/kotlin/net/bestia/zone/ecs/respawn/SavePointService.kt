package net.bestia.zone.ecs.respawn

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.bestia.PlayerBestiaRepository
import net.bestia.zone.bestia.findByIdOrThrow
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Where a dead player-owned entity belongs once it gets back up.
 *
 * Reads the database rather than caching the answer on the entity: a save point changes only at
 * character creation and at a world reset, and is needed only on the rare tick somebody actually
 * dies. Every caller runs off the tick thread, so the query is free of the simulation's timing
 * constraints.
 */
@Service
class SavePointService(
  private val masterRepository: MasterRepository,
  private val playerBestiaRepository: PlayerBestiaRepository,
) {

  @Transactional(readOnly = true)
  fun forMaster(masterId: Long): Vec3L {
    return masterRepository.findByIdOrThrow(masterId).spawnPosition
  }

  /**
   * Falls back to the owning master's save point when the bestia has none of its own. That is the
   * reading for every bestia created before the column existed: the schema is `ddl-auto: update`
   * against a live database, so those rows are still there with a zeroed embedded value, and a
   * respawn at the world origin would drop them in the drowned margin.
   */
  @Transactional(readOnly = true)
  fun forPlayerBestia(playerBestiaId: PlayerBestiaId): Vec3L {
    val playerBestia = playerBestiaRepository.findByIdOrThrow(playerBestiaId)

    if (playerBestia.spawnPosition != Vec3L.ZERO) {
      return playerBestia.spawnPosition
    }

    LOG.debug { "Player bestia $playerBestiaId has no save point, using its master's" }

    return playerBestia.master.spawnPosition
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
