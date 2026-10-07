package net.bestia.zone.master.bestia

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.PlayerBestiaPolicy
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.ecs.core.EntityIdGenerator
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.bestia.persistence.BestiaRepository
import net.bestia.zone.bestia.persistence.findByIdentifierOrThrow

@Component
class PlayerBestiaFactory(
  private val masterRepository: MasterRepository,
  private val bestiaRepository: BestiaRepository,
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val playerBestiaPolicy: PlayerBestiaPolicy,
  private val entityIdGenerator: EntityIdGenerator,
) {

  class PlayerBestiaCreateData(
    val bestiaIdentifier: String,
    val spawnPosition: Vec3L,
    val level: Int = 1
  )

  /**
   * Builds and persists a [PlayerBestia] row for the given master, enforcing [PlayerBestiaPolicy] so the
   * same bestia can never be owned twice.
   *
   * Persistence only - putting it into the world is [PlayerBestiaEntitySpawner]'s job, which
   * [PlayerBestiaCreateOperation] calls right after this.
   *
   * Transactional in its own right rather than relying on the caller to hold a session:
   * [net.bestia.zone.account.persistence.Master.addPlayerBestia] initializes the master's lazy `bestias`
   * collection, which fails outright on a detached master.
   */
  @Transactional
  fun create(
    masterId: Long,
    playerBestiaCreateData: PlayerBestiaCreateData,
  ): PlayerBestia {
    val master = masterRepository.findByIdOrThrow(masterId)
    val bestia = bestiaRepository.findByIdentifierOrThrow(playerBestiaCreateData.bestiaIdentifier)

    LOG.info { "Created PlayerBestia ${bestia.identifier} for master $master" }

    val pb = master.addPlayerBestia(bestia, playerBestiaPolicy)
    pb.position = playerBestiaCreateData.spawnPosition
    pb.spawnPosition = playerBestiaCreateData.spawnPosition
    pb.level = playerBestiaCreateData.level
    pb.entityId = entityIdGenerator.nextId()

    return playerBestiaRepository.save(pb)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
