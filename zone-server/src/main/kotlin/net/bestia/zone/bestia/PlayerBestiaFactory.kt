package net.bestia.zone.bestia

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.PlayerBestiaPolicy
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Component

@Component
class PlayerBestiaFactory(
  private val masterRepository: MasterRepository,
  private val bestiaRepository: BestiaRepository,
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val playerBestiaPolicy: PlayerBestiaPolicy
) {

  class PlayerBestiaCreateData(
    val bestiaIdentifier: String,
    val spawnPosition: Vec3L
  )

  /**
   * Spawns the given player bestia into the world.
   * It makes sure the same bestia can never be spawned twice.
   */
  fun create(
    masterId: Long,
    playerBestiaCreateData: PlayerBestiaCreateData,
  ): PlayerBestia {
    val master = masterRepository.findByIdOrThrow(masterId)
    val bestia = bestiaRepository.findByIdentifierOrThrow(playerBestiaCreateData.bestiaIdentifier)

    LOG.info { "Created PlayerBestia ${bestia.identifier} for master $master" }

    val pb = master.addPlayerBestia(bestia, playerBestiaPolicy)
    pb.position = playerBestiaCreateData.spawnPosition

    return playerBestiaRepository.save(pb)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
