package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.skill.LearnedSkillRepository
import net.bestia.zone.ecs.battle.AvailableSkills
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.account.Master as MasterComponent
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.battle.status.Level
import net.bestia.zone.ecs.battle.status.SkillPoints
import net.bestia.zone.ecs.account.MasterVisual
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.WorldView
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Creates an entity with all the required component from a master db entity.
 */
@Component
class MasterEntityFactory(
  private val world: WorldView,
  private val masterRepository: MasterRepository,
  private val learnedSkillRepository: LearnedSkillRepository,
  private val connectionInfoService: ConnectionInfoService,
) {

  /**
   * Creating a master is usually a two step process as we need to register him for the current
   * session before we start adding him to the zone server. Otherwise we would start sending out
   * updated and the master entity id is not yet registered to the session.
   */
  @Transactional(readOnly = true)
  fun createMasterEntity(masterId: Long): EntityId {
    val master = masterRepository.findByIdOrThrow(masterId)

    LOG.info { "Create master entity for account ${master.account.id} with master id: $masterId" }

    val learnedSkillIds = learnedSkillRepository.findAllByMasterId(masterId)
      .associate { it.skill.id to it.level }

    return world.createEntity { id ->
      connectionInfoService.activateSession(
        accountId = master.account.id,
        masterId = masterId,
        masterEntityId = id
      )

      add(id, Account(master.account.id))
      add(id, MasterComponent(master.id))
      add(id, Position.fromVec3(master.currentPosition))
      add(id, Level(master.level))
      add(id, Speed())
      add(id, Health(current = 10, max = 10))
      add(id, AvailableSkills(learnedSkillIds.toMutableMap()))
      add(id, SkillPoints(master.skillPoints))
      add(
        id,
        MasterVisual(
          id = master.id.toInt(),
          skinColor = master.skinColor,
          hairColor = master.hairColor,
          face = master.face,
          body = master.body,
          hair = master.hair
        )
      )
      add(id, buildInventory(master))
      add(id, ActivePlayer)
    }
  }

  private fun buildInventory(master: Master): Inventory {
    return Inventory(
      items = master.inventory.items.map { invItem ->
        Inventory.Item(
          itemId = invItem.playerItem.id.toInt(),
          amount = invItem.amount,
          uniqueId = 0
        )
      }.toMutableList()
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
