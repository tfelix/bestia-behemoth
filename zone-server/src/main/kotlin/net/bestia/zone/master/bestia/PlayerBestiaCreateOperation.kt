package net.bestia.zone.master.bestia

import net.bestia.zone.battle.status.IndividualValues
import net.bestia.zone.bestia.Sex
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
    val spawnPosition: Vec3L,
    val level: Int = 1,
    /** Hatched from an egg: what it was bred with. Null rolls them, as for a caught bestia. */
    val sex: Sex? = null,
    val individualValues: IndividualValues? = null,
    val inheritedAttacks: Map<Long, Int> = emptyMap(),
  )

  /**
   * One transaction spanning both halves, so the freshly created [net.bestia.zone.account.persistence.PlayerBestia] is still attached when
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
        spawnPosition = playerBestiaCreateData.spawnPosition,
        level = playerBestiaCreateData.level,
        sex = playerBestiaCreateData.sex,
        individualValues = playerBestiaCreateData.individualValues,
        inheritedAttacks = playerBestiaCreateData.inheritedAttacks,
      )
    )

    playerBestiaEntitySpawner.spawnPlayerBestia(pb)
  }
}