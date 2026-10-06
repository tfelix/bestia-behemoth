package net.bestia.zone.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.persistence.LearnedSkillRepository
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.battle.ecs.status.BaseStatusValues
import net.bestia.zone.battle.ecs.status.FormulaDrivenVitals
import net.bestia.zone.battle.ecs.status.StatusValues
import net.bestia.zone.item.ecs.CarryCapacity
import net.bestia.zone.item.ecs.WeightLimitCalculator
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.battle.status.ConditionValueCalculator
import net.bestia.zone.master.bestia.PlayerBestiaEntitySpawner
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.Speed
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.ActivePlayer
import net.bestia.zone.place.ecs.Place
import net.bestia.zone.place.ecs.PlaceNameService
import net.bestia.zone.identity.ecs.Master as MasterComponent
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.SkillPoints
import net.bestia.zone.battle.ecs.status.StatusPoints
import net.bestia.zone.master.ecs.MasterVisual
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.battle.ecs.exp.Exp
import net.bestia.zone.battle.ecs.level.LevelUpExperienceCalculator
import net.bestia.zone.logout.ecs.DisconnectProtection
import net.bestia.zone.logout.ecs.LogoutIntent
import net.bestia.zone.persistence.PersistAndRemove
import net.bestia.zone.persistence.Persistent
import net.bestia.zone.battle.persistence.StatusEffectPersistenceService
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.WorldView
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.party.membership

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
  private val placeNames: PlaceNameService,
  private val playerBestiaEntitySpawner: PlayerBestiaEntitySpawner,
) {

  /**
   * Creating a master is usually a two step process as we need to register him for the current
   * session before we start adding him to the zone server. Otherwise we would start sending out
   * updated and the master entity id is not yet registered to the session.
   *
   * Returns null when [accountId] does not own [masterId]: the id comes straight from the client.
   */
  @Transactional(readOnly = true)
  fun spawnMaster(accountId: AccountId, masterId: Long): EntityId? {
    val master = masterRepository.findByIdOrNull(masterId)

    if (master == null || master.account.id != accountId) {
      LOG.warn { "Account $accountId tried to select master $masterId, which it does not own" }
      return null
    }

    // Still in the world after a logout or disconnect that has not finished: picked up as it is, because a
    // reload from the database would roll it back to its last save and revive it if it died since.
    val reattached = world.modify(master.entityId) { id ->
      connectionInfoService.activateSession(accountId = accountId, masterId = masterId, masterEntityId = id)
      remove(id, LogoutIntent::class)
      remove(id, DisconnectProtection::class)
      remove(id, PersistAndRemove::class)
      id
    }
    if (reattached != null) {
      LOG.info { "Re-attached account $accountId to master $masterId, still in the world as $reattached" }
      return reattached
    }

    LOG.info { "Create master entity for account ${master.account.id} with master id: $masterId" }

    val learnedSkillIds = learnedSkillRepository.findAllByMasterId(masterId)
      .associate { it.skill.id to it.level }

    // Read before the world scope: everything inside createEntity holds the world, and must not do I/O -
    // the container's slots are a lazy relation.
    val persistedStatusEffects = statusEffectPersistenceService.load(master.entityId)
    val inventory = buildInventory(master)
    val equipment = buildEquipment(master)
    val bestias = playerBestiaEntitySpawner.loadOwnedBy(masterId)
    val partyMembership = master.party?.membership()

    return world.createEntity(master.entityId) { id ->
      // Before the session is read from the world, so it also gets the bestias a restart lost.
      playerBestiaEntitySpawner.respawnMissing(this, masterId, bestias)

      connectionInfoService.activateSession(
        accountId = master.account.id,
        masterId = masterId,
        masterEntityId = id,
        ownedBestias = OwnedBestia.ownedBy(this, masterId),
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
      add(id, inventory)
      add(id, equipment)
      partyMembership?.let { add(id, it) }

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

      // Resolved here rather than left to PlaceSystem's first tick, which would leave a player looking at
      // an empty location panel for a frame after every login. `clock.gd`'s replay problem in reverse:
      // there the message arrives before the HUD, here the HUD would arrive before the message.
      val spawn = master.currentPosition
      add(id, Place(placeNames.resolve(spawn.x, spawn.y)))

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
