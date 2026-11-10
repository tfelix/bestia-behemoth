package net.bestia.zone.breeding

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.breeding.persistence.BestiaEggRepository
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.identity.ecs.Master
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
import net.bestia.zone.util.EntityId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * What an Incubator's owner does at it: put an egg in, take it out. Only the owner, from next to it. Hatching is
 * [net.bestia.zone.breeding.ecs.IncubatorSystem]'s.
 */
@Service
class IncubatorService(
  private val structures: PlayerStructureRegistry,
  private val incubators: Incubators,
  private val skills: BreederSkills,
  private val itemTemplates: ItemTemplateRegistry,
  private val inventoryService: InventoryService,
  private val eggs: BestiaEggRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldView: WorldView,
  private val states: BreedingStateSender,
  private val clock: Clock = Clock.systemUTC(),
) {

  fun open(world: World, masterEntityId: EntityId, structureId: Long): OpError? {
    ownIncubatorNear(world, masterEntityId, structureId) ?: return OpError.BREEDING_NOT_POSSIBLE
    states.sendIncubator(world, structureId)
    return null
  }

  /** [egg] is read from its row by the caller; the egg item must be in the master's own bag. */
  fun place(world: World, masterEntityId: EntityId, structureId: Long, egg: EggSummary): OpError? {
    val structure = ownIncubatorNear(world, masterEntityId, structureId) ?: return OpError.BREEDING_NOT_POSSIBLE
    val incubator = incubators.of(structureId)
    if (incubator.egg != null) return OpError.BREEDING_NOT_POSSIBLE

    val eggItemId = itemTemplates.idOf(BreedingFarmService.BESTIA_EGG) ?: return OpError.BREEDING_NOT_POSSIBLE
    val inventory = world.get(masterEntityId, Inventory::class) ?: return OpError.BREEDING_NOT_POSSIBLE
    val held = inventory.getItems().firstOrNull { it.uniqueId == egg.instanceId && it.itemId == eggItemId }
      ?: return OpError.BREEDING_NOT_POSSIBLE
    inventory.removeByUniqueId(egg.instanceId)

    val bloodlineStudy = skills.bloodlineStudyLevel(world.get(masterEntityId, KnownSkills::class))
    incubators.put(
      Incubator(
        structureId = structureId,
        egg = egg,
        since = clock.instant(),
        reductionPercent = BloodlineStudy.timeReductionPercent(bloodlineStudy),
      )
    )
    states.sendIncubator(world, structureId)

    val masterId = structure.ownerMasterId
    asyncJobExecutor.submit(masterId) {
      // Detached rather than destroyed: the instance is the egg, and comes back out of the incubator as it is.
      if (inventoryService.removeOneFromMaster(masterId, eggItemId, 1, egg.instanceId) == null) {
        LOG.warn { "Master $masterId no longer held egg ${egg.instanceId}; taking it back out of incubator $structureId" }
        worldView.post {
          incubators.put(incubators.of(structureId).empty())
          worldView.modify(masterEntityId) { id -> get(id, Inventory::class)?.addItem(held) }
          states.sendIncubator(this, structureId)
        }
      }
    }

    return null
  }

  /** Into the owner's bag, with the progress it made kept in the egg. */
  fun takeEgg(world: World, masterEntityId: EntityId, structureId: Long): OpError? {
    val structure = ownIncubatorNear(world, masterEntityId, structureId) ?: return OpError.BREEDING_NOT_POSSIBLE
    val incubator = incubators.of(structureId)
    val egg = incubator.egg?.takeIf { !incubator.hatching } ?: return OpError.BREEDING_NOT_POSSIBLE
    val eggItemId = itemTemplates.idOf(BreedingFarmService.BESTIA_EGG) ?: return OpError.BREEDING_NOT_POSSIBLE
    val weight = itemTemplates.weightOf(eggItemId) ?: 0
    if (!CarryCheck.fits(world, masterEntityId, weight)) return OpError.BREEDING_NO_ROOM_FOR_EGG

    val hatchedMillis = incubator.hatchedMillisAt(clock.instant())
    incubators.put(incubator.empty())
    states.sendIncubator(world, structureId)

    val masterId = structure.ownerMasterId
    asyncJobExecutor.submit(masterId) {
      saveProgress(egg.instanceId, hatchedMillis)
      inventoryService.grantToMaster(masterId, eggItemId, 1, uniqueId = egg.instanceId)
      worldView.modify(masterEntityId) { id ->
        get(id, Inventory::class)?.addItem(EggItems.inventoryItem(eggItemId, weight, egg.instanceId))
      }
    }

    return null
  }

  /** Off the tick. Keeps how far an egg got, so the next incubator continues from there. */
  fun saveProgress(eggInstanceId: Long, hatchedMillis: Long) {
    val egg = eggs.findByIdOrNull(eggInstanceId) ?: return
    egg.hatchedMillis = hatchedMillis
    eggs.save(egg)
  }

  private fun ownIncubatorNear(world: World, masterEntityId: EntityId, structureId: Long): StructureEntry? {
    val incubator = structures.of(structureId)
      ?.takeIf { it.kind == StaticEntityKind.INCUBATOR && !it.isUnderConstruction } ?: return null
    val masterId = world.get(masterEntityId, Master::class)?.masterId
    val at = world.get(masterEntityId, Position::class)?.toVec3L()

    val isOwnerNearby = masterId == incubator.ownerMasterId &&
      at != null && at.distance(incubator.position) <= PlayerStructureService.RANGE_TILES

    return incubator.takeIf { isOwnerNearby }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
