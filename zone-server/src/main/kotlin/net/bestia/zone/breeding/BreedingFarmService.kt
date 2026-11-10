package net.bestia.zone.breeding

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.bestia.Sex
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.CarryCheck
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.master.skill.BloodlineStudy
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.travel.JourneyOrder
import net.bestia.zone.travel.JourneyService
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.WorldView
import org.springframework.stereotype.Service

/**
 * What a farm's owner does at the farm: send a pair, let it go, take the egg. Only the owner, from next to it.
 * Everything that happens on its own afterwards is [net.bestia.zone.breeding.ecs.BreedingFarmSystem]'s.
 */
@Service
class BreedingFarmService(
  private val structures: PlayerStructureRegistry,
  private val farms: BreedingFarms,
  private val journeys: JourneyService,
  private val skills: BreederSkills,
  private val itemTemplates: ItemTemplateRegistry,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldView: WorldView,
  private val states: BreedingStateSender,
) {

  fun open(world: World, masterEntityId: EntityId, structureId: Long): OpError? {
    ownFarmNear(world, masterEntityId, structureId) ?: return OpError.BREEDING_NOT_POSSIBLE
    states.sendFarm(world, structureId)
    return null
  }

  /**
   * Sends [motherEntityId] and [fatherEntityId] to the farm with a Nesting Bundle. [sexes] are read from their rows
   * by the caller, since a sex is not live state.
   */
  fun start(
    world: World,
    masterEntityId: EntityId,
    structureId: Long,
    motherEntityId: EntityId,
    fatherEntityId: EntityId,
    sexes: Map<EntityId, Sex>,
  ): OpError? {
    val structure = ownFarmNear(world, masterEntityId, structureId) ?: return OpError.BREEDING_NOT_POSSIBLE
    val farm = farms.of(structureId)
    if (farm.phase != FarmPhase.IDLE) return OpError.BREEDING_NOT_POSSIBLE

    val pair = pairOf(world, structure, motherEntityId, fatherEntityId, sexes)
    if (pair == null) {
      LOG.warn { "Master entity $masterEntityId offered $motherEntityId and $fatherEntityId, which cannot breed" }
      return OpError.BREEDING_NOT_POSSIBLE
    }

    val bundleItemId = itemTemplates.idOf(NESTING_BUNDLE) ?: return OpError.BREEDING_NOT_POSSIBLE
    val inventory = world.get(masterEntityId, Inventory::class) ?: return OpError.BREEDING_NOT_POSSIBLE
    if (!farm.hasBundle && inventory.getItems().none { it.itemId == bundleItemId && it.isStackable }) {
      return OpError.BREEDING_NEEDS_BUNDLE
    }

    for (parent in pair.entityIds) {
      if (isAtFarm(world, parent, structure)) continue
      if (journeys.start(world, parent, listOf(structure.position), JourneyOrder.PLAYER) != null) {
        pair.entityIds.forEach { journeys.cancel(world, it) }
        return OpError.BREEDING_PAIR_CANNOT_TRAVEL
      }
    }

    if (!farm.hasBundle) payBundle(world, masterEntityId, structure.ownerMasterId, inventory, bundleItemId)

    val known = world.get(masterEntityId, KnownSkills::class)
    val bloodlineStudy = skills.bloodlineStudyLevel(known)
    val clutchTime = BreedingTimes.reduced(
      BreedingTimes.breeding(pair.motherLevel, pair.fatherLevel),
      BloodlineStudy.timeReductionPercent(bloodlineStudy),
    )

    farms.put(
      farm.copy(
        phase = FarmPhase.GATHERING,
        pair = pair,
        hasBundle = true,
        clutchTime = clutchTime,
        endsAt = null,
        dominantIvs = BloodlineStudy.dominantIvs(bloodlineStudy),
        beastfriendLevel = skills.beastfriendLevel(known),
      )
    )
    states.sendFarm(world, structureId)
    return null
  }

  /** Lets the pair go. A clutch under way is lost with its bundle; a pair still walking keeps the bundle held. */
  fun release(world: World, masterEntityId: EntityId, structureId: Long): OpError? {
    ownFarmNear(world, masterEntityId, structureId) ?: return OpError.BREEDING_NOT_POSSIBLE
    val farm = farms.of(structureId)
    val pair = farm.pair ?: return OpError.BREEDING_NOT_POSSIBLE
    if (farm.phase == FarmPhase.LAYING) return OpError.BREEDING_NOT_POSSIBLE

    letGo(world, farm.structureId, pair, cancelJourneys = farm.phase == FarmPhase.GATHERING)
    val keepsEgg = farm.phase == FarmPhase.EGG_READY
    farms.put(farm.copy(phase = if (keepsEgg) FarmPhase.EGG_READY else FarmPhase.IDLE, pair = null, endsAt = null))

    states.sendFarm(world, structureId)
    return null
  }

  /** Into the owner's bag. The pair stays, so one more bundle breeds it again. */
  fun takeEgg(world: World, masterEntityId: EntityId, structureId: Long): OpError? {
    val structure = ownFarmNear(world, masterEntityId, structureId) ?: return OpError.BREEDING_NOT_POSSIBLE
    val farm = farms.of(structureId)
    val egg = farm.egg?.takeIf { farm.phase == FarmPhase.EGG_READY } ?: return OpError.BREEDING_NOT_POSSIBLE
    val eggItemId = itemTemplates.idOf(BESTIA_EGG) ?: return OpError.BREEDING_NOT_POSSIBLE
    val weight = itemTemplates.weightOf(eggItemId) ?: 0
    if (!CarryCheck.fits(world, masterEntityId, weight)) return OpError.BREEDING_NO_ROOM_FOR_EGG

    farms.put(farm.copy(phase = FarmPhase.IDLE, egg = null))
    states.sendFarm(world, structureId)

    val masterId = structure.ownerMasterId
    asyncJobExecutor.submit(masterId) {
      inventoryService.grantToMaster(masterId, eggItemId, 1, uniqueId = egg.instanceId)
      worldView.modify(masterEntityId) { id ->
        get(id, Inventory::class)?.addItem(EggItems.inventoryItem(eggItemId, weight, egg.instanceId))
      }
    }

    return null
  }

  /** Lets both parents go: their leash, and their walk to the farm when [cancelJourneys]. */
  fun letGo(world: World, structureId: Long, pair: BreedingPair, cancelJourneys: Boolean) {
    for (parent in pair.entityIds) {
      BreedingLeash.release(world, parent, structureId)
      if (cancelJourneys) journeys.cancel(world, parent)
    }
  }

  private fun pairOf(
    world: World,
    structure: StructureEntry,
    motherEntityId: EntityId,
    fatherEntityId: EntityId,
    sexes: Map<EntityId, Sex>,
  ): BreedingPair? {
    if (motherEntityId == fatherEntityId) return null
    if (sexes[motherEntityId] != Sex.FEMALE || sexes[fatherEntityId] != Sex.MALE) return null

    val mother = world.get(motherEntityId, OwnedBestia::class) ?: return null
    val father = world.get(fatherEntityId, OwnedBestia::class) ?: return null
    if (mother.masterId != structure.ownerMasterId || father.masterId != structure.ownerMasterId) return null
    if (listOf(mother, father).any { isPairedElsewhere(it.playerBestiaId, structure.id) }) return null
    if (world.has(motherEntityId, Dead::class) || world.has(fatherEntityId, Dead::class)) return null

    val motherKind = world.get(motherEntityId, Nature::class)?.kind ?: return null
    if (world.get(fatherEntityId, Nature::class)?.kind != motherKind) return null

    val motherLevel = world.get(motherEntityId, Level::class)?.level ?: return null
    val fatherLevel = world.get(fatherEntityId, Level::class)?.level ?: return null
    if (motherLevel < MIN_LEVEL || fatherLevel < MIN_LEVEL) return null

    return BreedingPair(
      mother.playerBestiaId, motherEntityId, motherLevel,
      father.playerBestiaId, fatherEntityId, fatherLevel,
    )
  }

  private fun isPairedElsewhere(playerBestiaId: Long, structureId: Long): Boolean {
    val farm = farms.pairedWith(playerBestiaId) ?: return false
    return farm.structureId != structureId
  }

  private fun payBundle(world: World, masterEntityId: EntityId, masterId: Long, inventory: Inventory, bundleItemId: Long) {
    val taken = checkNotNull(inventory.takeAcrossPiles(bundleItemId, 1))
    asyncJobExecutor.submit(masterId) {
      if (!inventoryService.consumeAll(masterId, listOf(bundleItemId to 1))) {
        LOG.warn { "Master $masterId lost the nesting bundle paid into a breeding farm; giving it back" }
        worldView.modify(masterEntityId) { id -> taken.forEach { get(id, Inventory::class)?.addItem(it) } }
      }
    }
  }

  private fun ownFarmNear(world: World, masterEntityId: EntityId, structureId: Long): StructureEntry? {
    val farm = structures.of(structureId)
      ?.takeIf { it.kind == StaticEntityKind.BREEDING_FARM && !it.isUnderConstruction } ?: return null
    val masterId = world.get(masterEntityId, Master::class)?.masterId
    val at = world.get(masterEntityId, Position::class)?.toVec3L()

    val isOwnerNearby = masterId == farm.ownerMasterId &&
      at != null && at.distance(farm.position) <= PlayerStructureService.RANGE_TILES

    return farm.takeIf { isOwnerNearby }
  }

  companion object {
    const val MIN_LEVEL = 10
    const val NESTING_BUNDLE = "nesting_bundle"
    const val BESTIA_EGG = "bestia_egg"

    /** Close enough to the farm to count as arrived, given its fence stops a walk short of the centre. */
    const val ARRIVE_DISTANCE = 6L

    fun isAtFarm(world: World, bestiaId: EntityId, farm: StructureEntry): Boolean {
      val at = world.get(bestiaId, Position::class)?.toVec3L() ?: return false
      return at.distance(farm.position) <= ARRIVE_DISTANCE
    }

    private val LOG = KotlinLogging.logger { }
  }
}
