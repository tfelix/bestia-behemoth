package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.skill.LearnedSkillRepository
import net.bestia.zone.ecs.battle.KnownSkills
import net.bestia.zone.ecs.battle.status.Attributes
import net.bestia.zone.ecs.item.CarryCapacity
import net.bestia.zone.ecs.item.WeightLimitCalculator
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.Mana
import net.bestia.zone.ecs.battle.status.Stamina
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.account.Master as MasterComponent
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.status.SkillPoints
import net.bestia.zone.ecs.account.MasterVisual
import net.bestia.zone.ecs.persistence.Persistent
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
  private val weightLimitCalculator: WeightLimitCalculator,
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
      add(id, Mana(current = 10, max = 10))
      add(id, Stamina(current = 10, max = 10))
      add(id, KnownSkills(learnedSkillIds.toMutableMap()))
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
      val inventory = buildInventory(master)
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
          current = inventory.totalWeight,
          max = weightLimitCalculator.computeWeightLimit(
            strength = attributes.strength,
            vitality = attributes.vitality,
            level = master.level
          )
        )
      )

      add(id, ActivePlayer)
      add(id, Persistent)
    }
  }

  private fun buildInventory(master: Master): Inventory {
    return Inventory(
      items = master.inventory.items.map { invItem ->
        Inventory.Item(
          itemId = invItem.playerItem.item.id,
          weight = invItem.playerItem.item.weight,
          amount = invItem.amount,
          playerItemId = invItem.playerItem.id
        )
      }.toMutableList()
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
