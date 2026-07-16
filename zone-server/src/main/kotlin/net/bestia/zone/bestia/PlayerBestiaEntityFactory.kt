package net.bestia.zone.bestia

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.KnownSkills
import net.bestia.zone.ecs.battle.status.Attributes
import net.bestia.zone.ecs.item.CarryCapacity
import net.bestia.zone.ecs.item.CarryCapacityService
import net.bestia.zone.ecs.item.Inventory
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
  private val connectionInfoService: ConnectionInfoService,
  private val carryCapacityService: CarryCapacityService,
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
      add(id, KnownSkills((fixedAttackIds + customAttackIds).toMutableMap()))

      val inventory = buildInventory(playerBestia)
      add(id, inventory)

      val attributes = Attributes(
        strength = 10,
        intelligence = 10,
        vitality = 10,
        dexterity = 10,
        willpower = 10,
        agility = 10
      )
      add(id, attributes)

      add(
        id,
        CarryCapacity(
          current = carryCapacityService.computeCurrentWeight(inventory.getItems()),
          max = carryCapacityService.computeWeightLimit(
            strength = attributes.strength,
            vitality = attributes.vitality,
            level = playerBestia.level
          )
        )
      )

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

  private fun buildInventory(playerBestia: PlayerBestia): Inventory {
    return Inventory(
      items = playerBestia.inventory.map { invItem ->
        Inventory.Item(
          itemId = invItem.playerItem.item.id,
          amount = invItem.amount,
          playerItemId = null
        )
      }.toMutableList()
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
