package net.bestia.zone.bestia

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.AvailableSkills
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.bestia.BestiaVisual
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.persistence.Persistent
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PlayerBestiaEntityFactory(
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val world: WorldView,
  private val connectionInfoService: ConnectionInfoService
) {

  /**
   * Spawns the given player bestia into the world.
   * It makes sure the same bestia can never be spawned twice.
   */
  @Transactional(readOnly = true)
  fun createPlayerBestiaEntity(
    playerBestiaId: PlayerBestiaId,
  ) {
    val playerBestia = playerBestiaRepository.findByIdOrThrow(playerBestiaId)

    createPlayerBestiaEntity(playerBestia)
  }

  fun createPlayerBestiaEntity(
    playerBestia: PlayerBestia,
  ) {
    val accountId = playerBestia.master.account.id

    val fixedAttackIds = playerBestia.bestia.skills
      .filter { it.requiredLevel <= playerBestia.level }
      .associate { it.skill.id to 1 }
    val customAttackIds = playerBestia.learnedSkills.associate { it.skill.id to it.level }

    // spawn the entity into the world
    val entityId = world.createEntity { id ->
      add(id, Position.fromVec3(playerBestia.position))
      add(id, Level(playerBestia.level))
      add(id, Speed())
      add(id, BestiaVisual(playerBestia.bestia.id))
      add(id, Account(accountId))
      add(id, AvailableSkills((fixedAttackIds + customAttackIds).toMutableMap()))
      add(id, Persistent)
    }

    val playerBestiaId = playerBestia.id
    val masterId = playerBestia.master.id

    LOG.info { "Spawned player bestia $playerBestiaId for account $accountId with entity id: $entityId" }

    connectionInfoService.registerPlayerBestiaEntity(
      accountId = accountId,
      masterId = masterId,
      playerBestiaId = playerBestiaId,
      playerBestiaEntityId = entityId
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
