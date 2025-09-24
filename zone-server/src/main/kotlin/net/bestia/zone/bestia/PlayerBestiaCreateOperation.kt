package net.bestia.zone.bestia

import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Component

/**
 * The creator not only creates a player bestia in the database it also adds it
 * immediatly to the ECS system as entity.
 */
@Component
class PlayerBestiaCreateOperation(
  private val playerBestiaFactory: PlayerBestiaFactory,
  private val playerBestiaEntityFactory: PlayerBestiaEntityFactory
) {

  class PlayerBestiaCreateData(
    val bestiaIdentifier: String,
    val spawnPosition: Vec3L
  )

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

    playerBestiaEntityFactory.createPlayerBestiaEntity(pb)
  }
}