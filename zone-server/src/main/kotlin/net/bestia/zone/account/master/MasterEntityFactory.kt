package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.skill.LearnedSkillRepository
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.item.CarryCapacity
import net.bestia.zone.ecs.item.WeightLimitCalculator
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.Mana
import net.bestia.zone.ecs.battle.status.Stamina
import net.bestia.zone.battle.status.ConditionValueCalculator
import net.bestia.zone.ecs.item.Equipment
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.account.Master as MasterComponent
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.status.SkillPoints
import net.bestia.zone.ecs.account.MasterVisual
import net.bestia.zone.ecs.battle.exp.Exp
import net.bestia.zone.ecs.battle.level.LevelUpExperienceCalculator
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
  private val levelUpExpCalculator: LevelUpExperienceCalculator,
  private val conditionValueCalculator: ConditionValueCalculator,
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
      add(id, Exp(0, levelUpExpCalculator.getRequiredExperience(master.level)))
      add(id, Speed())
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
      add(id, buildEquipment(master))

      val baseStatusValues = BaseStatusValues(
        strength = 10,
        intelligence = 10,
        vitality = 10,
        dexterity = 10,
        willpower = 10,
        agility = 10
      )
      add(id, baseStatusValues)
      add(
        id,
        StatusValues(
          strength = baseStatusValues.strength,
          intelligence = baseStatusValues.intelligence,
          vitality = baseStatusValues.vitality,
          dexterity = baseStatusValues.dexterity,
          willpower = baseStatusValues.willpower,
          agility = baseStatusValues.agility
        )
      )

      // Formula-driven pools: seeded full from level + attributes and kept fresh by
      // StatusValueRecalcSystem (gated on FormulaDrivenVitals) as level/attributes change.
      val maxHp = conditionValueCalculator.computeMaxHp(master.level, baseStatusValues.vitality)
      val maxMana = conditionValueCalculator.computeMaxMana(master.level, baseStatusValues.intelligence)
      val maxStamina = conditionValueCalculator.computeMaxStamina(
        master.level, baseStatusValues.vitality, baseStatusValues.strength, baseStatusValues.willpower
      )
      add(id, Health(current = maxHp, max = maxHp))
      add(id, Mana(current = maxMana, max = maxMana))
      add(id, Stamina(current = maxStamina, max = maxStamina))

      add(
        id,
        CarryCapacity(
          current = inventory.totalWeight,
          max = weightLimitCalculator.computeWeightLimit(
            strength = baseStatusValues.strength,
            vitality = baseStatusValues.vitality,
            level = master.level
          )
        )
      )

      add(id, ActivePlayer)
      add(id, Persistent)
    }
  }

  /**
   * A master physically has every slot - whether it may actually wear a given item is decided at
   * equip time by [net.bestia.zone.item.equip.EquipmentService] (later: by its learned skills),
   * not by a static mask like a bestia species has.
   */
  private fun buildEquipment(master: Master): Equipment {
    return Equipment(
      availableSlotMask = EquipmentSlots.ALL,
      worn = master.container.equipped().mapValues { (_, slot) ->
        Equipment.EquippedItem(
          itemId = slot.template.id,
          uniqueId = slot.uniqueId,
          upgradeLevel = slot.itemInstance?.upgradeLevel ?: 0
        )
      }.toMutableMap()
    )
  }

  private fun buildInventory(master: Master): Inventory {
    return Inventory(
      items = master.container.slots.map { slot ->
        Inventory.Item(
          itemId = slot.template.id,
          weight = slot.template.weight,
          amount = slot.amount,
          uniqueId = slot.uniqueId,
          stackable = slot.isStackable
        )
      }.toMutableList()
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
