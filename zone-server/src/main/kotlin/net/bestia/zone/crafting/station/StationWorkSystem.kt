package net.bestia.zone.crafting.station

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.crafting.CraftingService
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
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import java.time.Clock
import org.springframework.stereotype.Component as SpringComponent

/**
 * Works through every station's queue, one item at a time, while its worker is there to do it.
 *
 * The worker is the station's owner, while they play. Each item rolls on its own and spends its share of the
 * ingredients the station holds, made or failed. A queue that cannot go on pauses and says why; it goes on by
 * itself once the cause is gone.
 */
@SpringComponent
class StationWorkSystem(
  private val structures: PlayerStructureRegistry,
  private val recipes: RecipeRegistry,
  private val crafting: CraftingService,
  private val itemTemplates: ItemTemplateRegistry,
  private val connections: ConnectionInfoService,
  private val work: StationWork,
  private val states: StationStateSender,
  private val clock: Clock = Clock.systemUTC(),
) : System {

  // In ITEMS rather than ACTIONS so every move of the tick is done before the range check, and before the grant
  // path so a finished item is in the bag the same tick.
  override val phase = Phase.ITEMS
  override val before = setOf(ObtainItemIntentSystem::class)
  override val reads: ComponentClassSet = setOf(Position::class, Inventory::class, CarryCapacity::class)
  override val writes: ComponentClassSet = setOf(ObtainItemIntent.CreateItemIntent::class)

  override fun update(world: World, deltaTime: Float) {
    structures.all()
      .filter { it.queue.isNotEmpty() && !it.isUnderConstruction }
      .forEach { station -> advance(world, station, deltaTime) }
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

    val workerId = workerOf(station)
    val craftSeconds = workerId?.let { crafting.craftSecondsFor(world, it, it, recipe) }
    val pause = when {
      workerId == null -> StationPause.WORKER_OFFLINE
      !isNear(world, workerId, station) -> StationPause.WORKER_AWAY
      !station.canWorkAt(clock.instant()) -> StationPause.STATION_OUT
      !outputFits(world, workerId, recipe.output) -> StationPause.BAG_FULL
      craftSeconds == null -> StationPause.CANNOT_CRAFT
      else -> StationPause.NONE
    }
    if (pause != progress.pause) {
      progress.pause = pause
      states.send(station)
    }
    if (pause != StationPause.NONE) return

    progress.craftSeconds = craftSeconds!!
    progress.seconds += deltaTime
    if (progress.seconds < craftSeconds) return

    val refusal = crafting.produceFromStation(world, workerId!!, workerId, recipe)
    if (refusal != null) {
      LOG.debug { "Station ${station.id} cannot make ${recipe.identifier} for its worker: $refusal" }
      progress.pause = StationPause.CANNOT_CRAFT
      states.send(station)
      return
    }

    val held = station.held.toMutableMap()
    recipe.inputTotals.forEach { (itemId, amount) -> held.merge(itemId, -amount, Int::plus) }
    val rest = station.queue.drop(1)
    val queue = if (entry.left == 1) rest else listOf(entry.copy(done = entry.done + 1)) + rest

    progress.seconds = 0f
    structures.setStationContents(station.id, queue, held)
    structures.of(station.id)?.let(states::send)
  }

  /** The owner's master entity, while they play that master; nobody works a station of somebody offline. */
  private fun workerOf(station: StructureEntry): EntityId? {
    val accountId = station.ownerAccountId ?: return null
    if (!connections.isPlaying(accountId, station.ownerMasterId)) return null

    return connections.getSelectedMasterEntityId(accountId)
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
  }
}
