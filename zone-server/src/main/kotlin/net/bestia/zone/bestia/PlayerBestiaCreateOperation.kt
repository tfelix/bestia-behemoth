package net.bestia.zone.bestia

import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * The creator not only creates a player bestia in the database it also adds it
 * immediatly to the ECS system as entity.
 */
@Component
class PlayerBestiaCreateOperation(
  private val playerBestiaFactory: PlayerBestiaFactory,
  private val playerBestiaEntitySpawner: PlayerBestiaEntitySpawner
) {

  class PlayerBestiaCreateData(
    val bestiaIdentifier: String,
    val spawnPosition: Vec3L
  )

  /**
   * One transaction spanning both halves, so the freshly created [PlayerBestia] is still attached when
   * the spawner walks its bestia template, container slots and learned skills.
   */
  @Transactional
  fun createAndSpawn(
    masterId: Long,
    playerBestiaCreateData: PlayerBestiaCreateData,
  ) {
    val pb = playerBestiaFactory.create(
      masterId = masterId,
      playerBestiaCreateData = PlayerBestiaFactory.PlayerBestiaCreateData(
        bestiaIdentifier = playerBestiaCreateData.bestiaIdentifier,
        spawnPosition = playerBestiaCreateData.spawnPosition
      )
    )

    playerBestiaEntitySpawner.spawnPlayerBestia(pb)
  }
}