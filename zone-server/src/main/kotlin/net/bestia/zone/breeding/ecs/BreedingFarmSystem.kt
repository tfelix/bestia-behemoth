package net.bestia.zone.breeding.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.breeding.BreedingFarm
import net.bestia.zone.breeding.BreedingFarmService
import net.bestia.zone.breeding.BreedingStateSender
import net.bestia.zone.breeding.BreedingFarms
import net.bestia.zone.breeding.BreedingLeash
import net.bestia.zone.breeding.BreedingPair
import net.bestia.zone.breeding.EggLaying
import net.bestia.zone.breeding.EggSummary
import net.bestia.zone.breeding.FarmPhase
import net.bestia.zone.breeding.SeasonAtFarm
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.travel.Journey
import net.bestia.zone.travel.JourneyService
import net.bestia.zone.util.EntityId
import net.bestia.zone.weather.EnvironmentalExposureSystem
import java.time.Clock
import org.springframework.stereotype.Component as SpringComponent

/**
 * Runs every Breeding Farm on its own: a pair arriving, a clutch failing because a parent left, the timer ending.
 * The egg is rolled off the tick by [EggLaying] and handed back through [WorldView.post].
 */
@SpringComponent
class BreedingFarmSystem(
  private val structures: PlayerStructureRegistry,
  private val farms: BreedingFarms,
  private val service: BreedingFarmService,
  private val states: BreedingStateSender,
  private val eggLaying: EggLaying,
  private val seasons: SeasonAtFarm,
  private val itemTemplates: ItemTemplateRegistry,
  private val lootItems: LootItemEntitySpawner,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldView: WorldView,
  private val clock: Clock = Clock.systemUTC(),
) : System {

  override val phase = Phase.UPKEEP
  override val after = setOf(EnvironmentalExposureSystem::class)
  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val reads: ComponentClassSet =
    setOf(Position::class, Dead::class, Journey::class, KnownSkills::class) + BreedingLeash.READS
  override val writes: ComponentClassSet = BreedingLeash.WRITES + JourneyService.WRITES

  override fun update(world: World, deltaTime: Float) {
    for (farm in farms.all().toList()) {
      val structure = structures.of(farm.structureId)
      if (structure == null) {
        farm.pair?.let { service.letGo(world, farm.structureId, it, cancelJourneys = true) }
        farms.remove(farm.structureId)
        continue
      }

      val pair = farm.pair ?: continue
      when (farm.phase) {
        FarmPhase.GATHERING -> gather(world, farm, pair, structure)
        FarmPhase.BREEDING -> breed(world, farm, pair, structure)
        FarmPhase.LAYING -> {}
        FarmPhase.IDLE, FarmPhase.EGG_READY -> keepOrLetGo(world, farm, pair, structure)
      }
    }
  }

  private fun gather(world: World, farm: BreedingFarm, pair: BreedingPair, structure: StructureEntry) {
    val arrived = pair.entityIds.filter { BreedingFarmService.isAtFarm(world, it, structure) }
    val lost = pair.entityIds.filter { it !in arrived && !isOnItsWay(world, it) }

    if (lost.isNotEmpty()) {
      service.letGo(world, farm.structureId, pair, cancelJourneys = true)
      farms.put(farm.copy(phase = FarmPhase.IDLE, pair = null))
      states.notify(farm.structureId, OperationErrorSMSG(OpError.BREEDING_PAIR_CANNOT_TRAVEL))
      states.sendFarm(world, farm.structureId)
      return
    }

    if (arrived.size < pair.entityIds.size) return

    pair.entityIds.forEach { BreedingLeash.attach(world, it, farm.structureId, structure.position) }
    farms.put(farm.copy(phase = FarmPhase.BREEDING, hasBundle = false, endsAt = clock.instant().plus(farm.clutchTime)))
    states.sendFarm(world, farm.structureId)
  }

  private fun breed(world: World, farm: BreedingFarm, pair: BreedingPair, structure: StructureEntry) {
    if (!staysAtFarm(world, pair, structure)) {
      service.letGo(world, farm.structureId, pair, cancelJourneys = false)
      farms.put(farm.copy(phase = FarmPhase.IDLE, pair = null, endsAt = null))
      states.notify(farm.structureId, OperationErrorSMSG(OpError.BREEDING_CLUTCH_FAILED))
      states.sendFarm(world, farm.structureId)
      return
    }

    val endsAt = farm.endsAt ?: return
    if (clock.instant().isBefore(endsAt)) return

    val laying = farm.copy(phase = FarmPhase.LAYING)
    farms.put(laying)
    val season = seasons.at(structure.position)

    asyncJobExecutor.submit(farm.structureId) {
      val egg = runCatching { eggLaying.lay(laying, season) }
        .onFailure { LOG.error(it) { "Breeding farm ${farm.structureId} could not lay its egg" } }
        .getOrNull()
      worldView.post { laid(this, farm.structureId, egg, structure.position) }
    }
  }

  /** A farm knocked down while its egg was rolled leaves the egg where it stood. */
  private fun laid(world: World, structureId: Long, egg: EggSummary?, farmAt: Vec3L) {
    val farm = farms.of(structureId)

    if (structures.of(structureId) == null || farm.phase != FarmPhase.LAYING) {
      egg?.let { dropEgg(world, it, farmAt) }
      return
    }

    farms.put(farm.copy(phase = if (egg == null) FarmPhase.IDLE else FarmPhase.EGG_READY, egg = egg, endsAt = null))
    if (egg != null) states.notify(structureId, OperationSuccessSMSG(OpSuccess.BREEDING_EGG_LAID))
    states.sendFarm(world, structureId)
  }

  /** Between clutches the pair stays leashed; one that left is simply let go, as nothing is lost. */
  private fun keepOrLetGo(world: World, farm: BreedingFarm, pair: BreedingPair, structure: StructureEntry) {
    if (staysAtFarm(world, pair, structure)) return

    service.letGo(world, farm.structureId, pair, cancelJourneys = false)
    farms.put(farm.copy(pair = null))
    states.sendFarm(world, farm.structureId)
  }

  /** Re-leashes a parent that came back into the world after a restart without its leash. */
  private fun staysAtFarm(world: World, pair: BreedingPair, structure: StructureEntry): Boolean {
    return pair.entityIds.all { parent ->
      val at = world.get(parent, Position::class)?.toVec3L()
      val stays = at != null && at.distance(structure.position) <= STRAY_DISTANCE &&
        !world.has(parent, Dead::class) && !world.has(parent, Journey::class) &&
        !BreedingLeash.isWorkingElsewhere(world, parent, structure.id)

      if (stays && !BreedingLeash.isLeashedTo(world, parent, structure.id)) {
        BreedingLeash.attach(world, parent, structure.id, structure.position)
      }
      stays
    }
  }

  private fun isOnItsWay(world: World, bestiaId: EntityId): Boolean {
    return world.has(bestiaId, Journey::class) && !world.has(bestiaId, Dead::class)
  }

  private fun dropEgg(world: World, egg: EggSummary, at: Vec3L) {
    val eggItemId = itemTemplates.idOf(BreedingFarmService.BESTIA_EGG) ?: return
    lootItems.spawnLootItem(world, itemId = eggItemId, amount = 1, pos = at, uniqueId = egg.instanceId)
  }

  private companion object {
    /** Further than its leash lets it wander: the owner drove it off, or it was taken somewhere. */
    const val STRAY_DISTANCE = 12L

    val LOG = KotlinLogging.logger { }
  }
}
