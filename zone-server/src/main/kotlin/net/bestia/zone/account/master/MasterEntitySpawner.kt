package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.skill.LearnedSkillRepository
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.battle.status.FormulaDrivenVitals
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.item.CarryCapacity
import net.bestia.zone.ecs.item.WeightLimitCalculator
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.IsStatusValueDirty
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
import net.bestia.zone.ecs.battle.status.StatusPoints
import net.bestia.zone.ecs.account.MasterVisual
import net.bestia.zone.ecs.battle.exp.Exp
import net.bestia.zone.ecs.battle.level.LevelUpExperienceCalculator
import net.bestia.zone.ecs.persistence.Persistent
import net.bestia.zone.ecs.persistence.StatusEffectPersistenceService
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.WorldView
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Materializes an already persisted [Master] row into a live ECS entity carrying every component a player
 * master needs.
 *
 * The world half only - it reads (`readOnly = true`) and writes nothing back. Creating the row in the first
 * place is [MasterFactory]'s job, driven by a different message: `CreateMasterCMSG` writes the master,
 * `SelectMasterCMSG` spawns it.
 */
@Component
class MasterEntitySpawner(
  private val world: WorldView,
  private val masterRepository: MasterRepository,
  private val learnedSkillRepository: LearnedSkillRepository,
  private val connectionInfoService: ConnectionInfoService,
  private val weightLimitCalculator: WeightLimitCalculator,
  private val levelUpExpCalculator: LevelUpExperienceCalculator,
  private val conditionValueCalculator: ConditionValueCalculator,
  private val statusEffectPersistenceService: StatusEffectPersistenceService,
) {

  /**
   * Creating a master is usually a two step process as we need to register him for the current
   * session before we start adding him to the zone server. Otherwise we would start sending out
   * updated and the master entity id is not yet registered to the session.
   */
  @Transactional(readOnly = true)
  fun spawnMaster(masterId: Long): EntityId {
    val master = masterRepository.findByIdOrThrow(masterId)

    LOG.info { "Create master entity for account ${master.account.id} with master id: $masterId" }

    // The master reuses the entity id it was stamped with at creation, so anything stored against it
    // (persisted status effects) finds it again. The flip side is that a leftover entity from a previous
    // session now collides instead of being quietly orphaned by a freshly minted id, and
    // `createEntity(id)` throws on a duplicate - so an incumbent is cleared out first. It is about to be
    // replaced by state read straight from the database anyway.
    if (world.hasEntity(master.entityId)) {
      LOG.warn { "Master $masterId still holds entity ${master.entityId} from a previous session, replacing it" }

      world.modify(master.entityId) { id -> destroy(id) }
    }

    val learnedSkillIds = learnedSkillRepository.findAllByMasterId(masterId)
      .associate { it.skill.id to it.level }

    // Read before taking the world lock: the attach below runs inside createEntity and must not do I/O.
    val persistedStatusEffects = statusEffectPersistenceService.load(master.entityId)

    return world.createEntity(master.entityId) { id ->
      connectionInfoService.activateSession(
        accountId = master.account.id,
        masterId = masterId,
        masterEntityId = id
      )

      add(id, Account(accountId = master.account.id))
      add(id, MasterComponent(master.id, master.name))
      add(id, Position.fromVec3(master.currentPosition))
      add(id, Level(master.level))
      add(id, Exp(master.exp, levelUpExpCalculator.getRequiredExperience(master.level)))
      add(id, Speed())
      add(id, KnownSkills(learnedSkillIds.toMutableMap()))
      add(id, SkillPoints(master.skillPoints))
      add(id, StatusPoints(master.statusPoints))
      add(
        id,
        MasterVisual(
          id = master.id.toInt(),
          name = master.name,
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
        strength = master.strength,
        intelligence = master.intelligence,
        vitality = master.vitality,
        dexterity = master.dexterity,
        willpower = master.willpower,
        agility = master.agility
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

      val maxHp = conditionValueCalculator.computeMaxHp(master.level, baseStatusValues.vitality)
      val maxMana = conditionValueCalculator.computeMaxMana(master.level, baseStatusValues.intelligence)
      val maxStamina = conditionValueCalculator.computeMaxStamina(
        master.level, baseStatusValues.vitality, baseStatusValues.strength, baseStatusValues.willpower
      )
      // Coerced to at least 1: a master must never materialise already dead, with no way back out.
      // Null is a row written before the column existed, and enters the world at full.
      add(id, Health(current = master.currentHealth?.coerceIn(1, maxHp) ?: maxHp, max = maxHp))
      add(id, Mana(current = maxMana, max = maxMana))
      add(id, Stamina(current = maxStamina, max = maxStamina))
      add(id, FormulaDrivenVitals)

      // The pools above are seeded from the *base* attributes, because nothing worn, buffed or
      // learned has been folded in yet. This asks for a recalc on the first tick so passives and
      // equipment actually take effect. Consequence to know about: where those raise a maximum,
      // `CurMax.max` lifts the ceiling without lifting `current`, so a geared master enters the
      // world a few points short of full and regenerates the difference within a tick or two - the
      // same trade GainExpSystem already makes on level-up.
      add(id, IsStatusValueDirty)

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

      // Whatever the master was carrying when it last left the world, plus anything seeded for it
      // before it ever entered - MasterFactory puts MASTER_INTRO_MARKER here at creation. Nothing is
      // applied unconditionally any more, so an effect that ran its course stays gone.
      // `this` is the World the create block runs against (WorldView.createEntity).
      statusEffectPersistenceService.attach(this, id, persistedStatusEffects)
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
          upgradeLevel = slot.itemInstance?.upgradeLevel ?: 0,
          durability = slot.durability,
          maxDurability = slot.maxDurability,
          slots = slot.slots
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
          stackable = slot.isStackable,
          equipped = slot.isEquipped,
          durability = slot.durability,
          maxDurability = slot.maxDurability,
          slots = slot.slots,
          upgradeLevel = slot.itemInstance?.upgradeLevel ?: 0
        )
      }.toMutableList()
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
