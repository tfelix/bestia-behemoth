package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.attack.LearnedSkillRepository
import net.bestia.zone.ecs.battle.AvailableAttacks
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.battle.LearnedSkills
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.player.Account
import net.bestia.zone.ecs.player.ActivePlayer
import net.bestia.zone.ecs.player.Master as MasterComponent
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.ecs.status.Level
import net.bestia.zone.ecs.status.SkillPoints
import net.bestia.zone.ecs.player.MasterVisual
import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Creates an entity with all the required component from a master db entity.
 */
@Component
class MasterEntityFactory(
  private val world: World,
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

      world.add(id, Account(master.account.id))
      world.add(id, MasterComponent(master.id))
      world.add(id, Position.fromVec3(master.position))
      world.add(id, Level(master.level))
      world.add(id, Speed())
      world.add(id, Health(current = 10, max = 10))
      world.add(id, AvailableAttacks(learnedSkillIds.toMutableMap()))
      world.add(id, LearnedSkills(learnedSkillIds.toMutableMap()))
      world.add(id, SkillPoints(master.skillPoints))
      world.add(
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
      world.add(id, buildInventory(master))
      world.add(id, ActivePlayer)
    }
  }

  private fun buildInventory(master: Master): Inventory {
    return Inventory(
      items = master.inventory.items.map { invItem ->
        Inventory.Item(
          itemId = invItem.item.id.toInt(),
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
