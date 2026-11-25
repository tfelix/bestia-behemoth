package net.bestia.zone.account

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.PlayerBestiaId
import net.bestia.zone.world.SpawnPointAvailability
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Where a dead player-owned entity belongs once it gets back up. A master without a save point gets the nearest
 * start town as one, the first time anybody asks.
 *
 * Reads the database rather than caching the answer on the entity: a save point changes rarely and is needed only
 * on the rare tick somebody actually dies. Every caller runs off the tick thread.
 */
@Service
class SavePointService(
  private val masterRepository: MasterRepository,
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val startTowns: SpawnPointAvailability,
) {

  @Transactional
  fun forMaster(masterId: Long): Vec3L {
    val master = masterRepository.findByIdOrThrow(masterId)

    return savePointOf(master, near = master.currentPosition)
  }

  /**
   * Falls back to the owning master's save point when the bestia has none of its own. That is the
   * reading for every bestia created before the column existed: the schema is `ddl-auto: update`
   * against a live database, so those rows are still there with a zeroed embedded value, and a
   * respawn at the world origin would drop them in the drowned margin.
   */
  @Transactional
  fun forPlayerBestia(playerBestiaId: PlayerBestiaId): Vec3L {
    val playerBestia = playerBestiaRepository.findByIdOrThrow(playerBestiaId)

    if (playerBestia.spawnPosition != Vec3L.ZERO) {
      return playerBestia.spawnPosition
    }

    LOG.debug { "Player bestia $playerBestiaId has no save point, using its master's" }
    val master = playerBestia.master

    return savePointOf(master, near = master.currentPosition)
  }

  /**
   * Gives [master] the start town nearest to [near] when it has no save point. Written through the entity, so the
   * caller's transaction saves it; `@DynamicUpdate` keeps that write to the save point columns.
   */
  fun savePointOf(master: Master, near: Vec3L): Vec3L {
    if (master.spawnPosition != Vec3L.ZERO) {
      return master.spawnPosition
    }

    val nearest = startTowns.offered().minByOrNull { it.position.distance(near) }
    if (nearest == null) {
      LOG.error { "Master ${master.id} has no save point and the world offers no start town to give it" }

      return Vec3L.ZERO
    }

    LOG.info { "Master ${master.id} had no save point, giving it ${nearest.settlementName} at ${nearest.position}" }
    master.spawnPosition = nearest.position
    master.homeSettlementName = nearest.settlementName

    return nearest.position
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
