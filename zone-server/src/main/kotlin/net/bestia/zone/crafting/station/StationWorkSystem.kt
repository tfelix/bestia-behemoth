package net.bestia.zone.crafting.station

import net.bestia.zone.bestia.diary.BestiaNews
import net.bestia.zone.bestia.diary.BestiaHappened
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.crafting.CraftingService
import net.bestia.zone.crafting.Crafters
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.prop.station.WorkAssignment
import net.bestia.zone.crafting.Recipe
import net.bestia.zone.crafting.RecipeRegistry
import net.bestia.zone.item.ecs.ObtainItemIntentSystem
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.CarryCapacity
import net.bestia.zone.item.ecs.CarryCheck
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.ecs.ObtainItemIntent
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.CampfireService
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.anchor.SimulationAnchor
import java.time.Clock
import org.springframework.stereotype.Component as SpringComponent

/**
 * Works through every station's queue, one item at a time, while its worker is there to do it.
 *
 * The worker is one of the owner's bestias, which keeps working while its master is logged out, or else the owner
 * while they play. Each item rolls on its own and spends its share of the ingredients the station holds, made or
 * failed. A queue that cannot go on pauses and says why; it goes on by itself once the cause is gone.
 */
@SpringComponent
class StationWorkSystem(
  private val structures: PlayerStructureRegistry,
  private val recipes: RecipeRegistry,
  private val crafting: CraftingService,
  private val crafters: Crafters,
  private val profiles: CrafterProfiles,
  private val workers: StationWorkers,
  private val itemTemplates: ItemTemplateRegistry,
  private val connections: ConnectionInfoService,
  private val work: StationWork,
  private val states: StationStateSender,
  private val campfires: CampfireService,
  private val clock: Clock = Clock.systemUTC(),
  private val news: BestiaNews = BestiaNews { },
) : System {

  // In ITEMS rather than ACTIONS so every move of the tick is done before the range check, and before the grant
  // path so a finished item is in the bag the same tick.
  override val phase = Phase.ITEMS
  override val before = setOf(ObtainItemIntentSystem::class)
  override val reads: ComponentClassSet =
    setOf(Position::class, Inventory::class, CarryCapacity::class, Master::class, AiAgent::class)
  override val writes: ComponentClassSet =
    setOf(ObtainItemIntent.CreateItemIntent::class, WorkAssignment::class, SimulationAnchor::class)

  override fun update(world: World, deltaTime: Float) {
    structures.all()
      .filter { it.queue.isNotEmpty() && it.kind == StaticEntityKind.CAMPFIRE && it.workerBestiaId != null }
      .forEach { fire -> keepBurning(world, fire) }

    structures.all()
      .filter { it.queue.isNotEmpty() && !it.isUnderConstruction }
      .forEach { station -> advance(world, station, deltaTime) }

    structures.all()
      .filter { it.takesAnyCrafter && it.workerBestiaId != null && it.queue.isEmpty() }
      .forEach { station -> releaseCrafter(world, station) }
  }

  /** A bestia at a campfire with work feeds it from the chest next to it, as its master would. */
  private fun keepBurning(world: World, fire: StructureEntry) {
    val workerId = workerOf(world, fire) ?: return
    if (isNear(world, workerId, fire)) campfires.addWoodFromChest(world, fire.id)
  }

  /** Tells a bestia worker its bag is in the way, so it carries its products to the chest. */
  private fun askToEmptyBag(world: World, workerId: EntityId) {
    val memory = world.get(workerId, AiAgent::class)?.memory ?: return
    memory.set(BestiaDomain.NO_ROOM_FOR_PRODUCT, true, BestiaDomain.NO_ROOM_FOR_PRODUCT.retain)
  }

  /** A free crafter whose station has nothing left to do moves on to another, or goes home. */
  private fun releaseCrafter(world: World, station: StructureEntry) {
    workers.unassign(world, station.id)
    structures.of(station.id)?.let(states::send)
  }

  private fun advance(world: World, station: StructureEntry, deltaTime: Float) {
    val progress = work.of(station.id)
    val entry = station.queue.first()
    val recipe = recipes.of(entry.recipeId)
    if (recipe == null) {
      LOG.warn { "Station ${station.id} queues recipe ${entry.recipeId}, which no longer exists; dropping it" }
      structures.setStationContents(station.id, station.queue.drop(1), station.held)
      return
    }

    val workerId = workerOf(world, station)
    val crafter = workerId?.let { crafterOf(world, it, station) }
    val pause = when {
      station.waitsForCrafter -> StationPause.NO_FREE_CRAFTER
      workerId == null || crafter == null -> StationPause.WORKER_OFFLINE
      !isNear(world, workerId, station) -> StationPause.WORKER_AWAY
      !station.canWorkAt(clock.instant()) -> StationPause.STATION_OUT
      !outputFits(world, workerId, recipe.output) -> StationPause.BAG_FULL
      else -> StationPause.NONE
    }
    if (pause != progress.pause) {
      progress.pause = pause
      states.send(station)
      tellStopped(station, pause)
    }
    if (pause == StationPause.BAG_FULL) askToEmptyBag(world, workerId!!)
    if (pause != StationPause.NONE) return

    val craftSeconds = crafting.craftSecondsFor(crafter!!, recipe)
    progress.craftSeconds = craftSeconds
    progress.seconds += deltaTime
    if (progress.seconds < craftSeconds) return

    val refusal = crafting.produceFromStation(world, workerId!!, crafter, recipe)
    if (refusal != null) {
      LOG.debug { "Station ${station.id} cannot make ${recipe.identifier} for its worker: $refusal" }
      progress.pause = StationPause.CANNOT_CRAFT
      states.send(station)
      tellStopped(station, StationPause.CANNOT_CRAFT)
      return
    }

    val held = station.held.toMutableMap()
    recipe.inputTotals.forEach { (itemId, amount) -> held.merge(itemId, -amount, Int::plus) }
    val rest = station.queue.drop(1)
    val queue = if (entry.left == 1) rest else listOf(entry.copy(done = entry.done + 1)) + rest

    progress.seconds = 0f
    structures.setStationContents(station.id, queue, held)
    structures.of(station.id)?.let(states::send)
    if (queue.isEmpty()) tell(station, BestiaHappened.Kind.QUEUE_FINISHED)
  }

  /** Only the pauses that last until somebody acts; a bag full or a worker off to eat sorts itself out. */
  private fun tellStopped(station: StructureEntry, pause: StationPause) {
    if (pause in LASTING_PAUSES) tell(station, BestiaHappened.Kind.WORK_PAUSED, pause.name)
  }

  /** Told to the diary of the bestia that works [station]; the owner working it themself is there to see. */
  private fun tell(station: StructureEntry, kind: BestiaHappened.Kind, reason: String? = null) {
    val worker = station.workerBestiaId ?: return
    news.tell(BestiaHappened(this, station.ownerMasterId, worker, kind, place = station.kind.name, reason = reason))
  }

  /** The bestia set to work here, or else the owner's master while they play it. */
  private fun workerOf(world: World, station: StructureEntry): EntityId? {
    if (station.workerBestiaId != null) {
      return workers.entityOf(station.id)?.takeIf { world.get(it, WorkAssignment::class)?.structureId == station.id }
    }

    return playingMasterOf(station)
  }

  private fun playingMasterOf(station: StructureEntry): EntityId? {
    val accountId = station.ownerAccountId ?: return null
    if (!connections.isPlaying(accountId, station.ownerMasterId)) return null

    return connections.getSelectedMasterEntityId(accountId)
  }

  /**
   * Skills and blueprints are always the master's. While the master plays they come from its entity, and are kept
   * for when it logs out; a bestia of an absent master crafts with the kept copy.
   */
  private fun crafterOf(world: World, workerId: EntityId, station: StructureEntry): Crafters.Crafter? {
    val masterEntityId = playingMasterOf(station)
      ?: return profiles.crafterFor(world, workerId, station.ownerMasterId)

    profiles.remember(world, masterEntityId)
    return crafters.of(world, workerId, masterEntityId)
  }

  private fun isNear(world: World, workerId: EntityId, station: StructureEntry): Boolean {
    val at = world.get(workerId, Position::class)?.toVec3L() ?: return false

    return at.distance(station.position) <= PlayerStructureService.RANGE_TILES
  }

  private fun outputFits(world: World, workerId: EntityId, output: Recipe.ItemStack?): Boolean {
    if (output == null) return true

    val weight = itemTemplates.templateOf(output.itemId)?.weight ?: 0
    return CarryCheck.fits(world, workerId, weight * output.amount)
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    val LASTING_PAUSES = setOf(StationPause.STATION_OUT, StationPause.CANNOT_CRAFT)
  }
}
