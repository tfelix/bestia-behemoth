package net.bestia.zone.crafting.station

import net.bestia.zone.prop.station.QueueEntry
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.crafting.CraftingService
import net.bestia.zone.crafting.Crafters
import net.bestia.zone.crafting.RecipeRegistry
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.CarryCheck
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.ecs.ObtainItemIntent
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.StationSpill
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Filling and emptying a station's queue. Only its owner may, from next to it.
 *
 * Queuing pays the ingredients for the whole amount into the station at once: off the live bag now, out of the
 * database afterwards, the order `CraftingService` uses. So nothing runs short halfway, and whoever works the
 * queue needs none of it in their own bag.
 */
@Service
class StationQueueService(
  private val structures: PlayerStructureRegistry,
  private val recipes: RecipeRegistry,
  private val crafting: CraftingService,
  private val crafters: Crafters,
  private val workers: StationWorkers,
  private val itemTemplates: ItemTemplateRegistry,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val lootItems: LootItemEntitySpawner,
  private val work: StationWork,
  private val states: StationStateSender,
) : StationSpill {

  /** @return the refusal to send back, or null when the entry is queued */
  fun enqueue(world: World, actorId: EntityId, structureId: Long, recipeId: Long, amount: Int): OpError? {
    val station = ownStationNear(world, actorId, structureId) ?: return OpError.CRAFT_NOT_POSSIBLE
    val recipe = recipes.of(recipeId)?.takeIf { it.station == station.kind } ?: return OpError.CRAFT_NOT_POSSIBLE
    if (station.queue.size >= MAX_ENTRIES) return OpError.CRAFT_ALREADY_IN_PROGRESS

    val crafter = crafters.of(world, actorId, actorId) ?: return OpError.CRAFT_NOT_POSSIBLE
    crafting.refusalToQueue(crafter, recipe)?.let { return it }

    val count = amount.coerceIn(1, MAX_AMOUNT)
    val paid = recipe.inputTotals.mapValues { (_, each) -> each * count }
    val inventory = world.get(actorId, Inventory::class) ?: return OpError.CRAFT_NOT_POSSIBLE
    if (paid.any { (itemId, needed) -> heldAmount(inventory, itemId) < needed }) return OpError.CRAFT_MISSING_MATERIALS

    val taken = paid.flatMap { (itemId, needed) -> checkNotNull(inventory.takeAcrossPiles(itemId, needed)) }
    structures.setStationContents(structureId, station.queue + QueueEntry(recipeId, count), station.held + paid)

    val masterId = station.ownerMasterId
    asyncJobExecutor.submit(masterId) {
      if (!inventoryService.consumeAll(masterId, paid.toList())) {
        LOG.warn { "Master $masterId lost the ingredients queued at station $structureId; giving them back" }
        world.modify(actorId) { id ->
          taken.forEach { get(id, Inventory::class)?.addItem(it) }
          unpay(structureId, paid)
        }
      }
    }

    sendState(structureId)
    return null
  }

  /**
   * Takes the entry at [index] off the queue. Its unused ingredients go back into the owner's bag as far as they
   * fit; the rest stays in the station until there is room.
   *
   * @return the refusal to send back, or null when the entry is gone
   */
  fun cancel(world: World, actorId: EntityId, structureId: Long, index: Int): OpError? {
    val station = ownStationNear(world, actorId, structureId) ?: return OpError.CRAFT_NOT_POSSIBLE
    val entry = station.queue.getOrNull(index) ?: return OpError.CRAFT_NOT_POSSIBLE
    val recipe = recipes.of(entry.recipeId)

    val unused = recipe?.inputTotals.orEmpty().mapValues { (itemId, each) ->
      minOf(each * entry.left, station.held[itemId] ?: 0)
    }
    val refunded = fitting(world, actorId, unused)
    if (refunded.isNotEmpty()) {
      world.add(actorId, ObtainItemIntent.CreateItemsIntent(refunded.map { (itemId, amount) ->
        ObtainItemIntent.CreateItemIntent(itemId, amount)
      }))
    }

    val held = station.held.toMutableMap()
    refunded.forEach { (itemId, amount) -> held.merge(itemId, -amount, Int::plus) }
    structures.setStationContents(structureId, station.queue.filterIndexed { i, _ -> i != index }, held)
    if (index == 0) work.forget(structureId)

    sendState(structureId)
    return null
  }

  /**
   * Picks who works the station: [workerEntityId] for one of the owner's bestias, 0 for the owner.
   *
   * @return the refusal to send back, or null when the worker is set
   */
  fun setWorker(world: World, actorId: EntityId, structureId: Long, workerEntityId: EntityId): OpError? {
    val station = ownStationNear(world, actorId, structureId) ?: return OpError.CRAFT_NOT_POSSIBLE

    if (workerEntityId == 0L) {
      workers.unassign(world, structureId)
    } else {
      val owned = world.get(workerEntityId, OwnedBestia::class)
      if (owned?.masterId != station.ownerMasterId) return OpError.CRAFT_NOT_POSSIBLE
      workers.assign(world, workerEntityId, station)
    }
    structures.setTakesAnyCrafter(structureId, false)

    work.forget(structureId)
    sendState(structureId)
    return null
  }

  /**
   * Lets the first idle crafter of the owner whose area covers the station take it. Whoever works it now stops.
   *
   * @return the refusal to send back, or null when the station waits for a crafter
   */
  fun takeAnyFreeCrafter(world: World, actorId: EntityId, structureId: Long): OpError? {
    ownStationNear(world, actorId, structureId) ?: return OpError.CRAFT_NOT_POSSIBLE

    workers.unassign(world, structureId)
    structures.setTakesAnyCrafter(structureId, true)

    work.forget(structureId)
    sendState(structureId)
    return null
  }

  /** Drops what a station holds on the ground where it stood: it was knocked down, or its fire is gone. */
  override fun spill(world: World, station: StructureEntry, at: Vec3L) {
    station.held.forEach { (itemId, amount) -> lootItems.spawnLootItem(world, itemId = itemId, amount = amount, pos = at) }
    work.forget(station.id)
    workers.unassign(world, station.id)

    if (station.held.isNotEmpty()) LOG.info { "Station ${station.id} spilled ${station.held} at $at" }
  }

  fun sendState(structureId: Long) {
    structures.of(structureId)?.let(states::send)
  }

  private fun ownStationNear(world: World, actorId: EntityId, structureId: Long): StructureEntry? {
    val station = structures.of(structureId)?.takeIf { !it.isUnderConstruction } ?: return null
    val masterId = world.get(actorId, Master::class)?.masterId
    val at = world.get(actorId, Position::class)?.toVec3L()

    val isOwnerNearby = masterId == station.ownerMasterId &&
      at != null && at.distance(station.position) <= PlayerStructureService.RANGE_TILES

    return station.takeIf { isOwnerNearby }
  }

  /** How much of each pile in [piles] fits in [entityId]'s bag, filled in order. */
  private fun fitting(world: World, entityId: EntityId, piles: Map<Long, Int>): Map<Long, Int> {
    val fitting = mutableMapOf<Long, Int>()
    var added = 0

    for ((itemId, amount) in piles) {
      val weight = itemTemplates.templateOf(itemId)?.weight ?: 0
      val count = (amount downTo 1).firstOrNull { CarryCheck.fits(world, entityId, added + weight * it) } ?: continue
      fitting[itemId] = count
      added += weight * count
    }

    return fitting
  }

  /** Takes the last entry and its ingredients back off a station whose durable payment failed. */
  private fun unpay(structureId: Long, paid: Map<Long, Int>) {
    val station = structures.of(structureId) ?: return
    val held = station.held.toMutableMap()
    paid.forEach { (itemId, amount) -> held.merge(itemId, -amount, Int::plus) }

    structures.setStationContents(structureId, station.queue.dropLast(1), held)
  }

  /** Stackable piles only, as `CraftingService` counts them: the database can only take from stacks. */
  private fun heldAmount(inventory: Inventory, itemId: Long): Int {
    return inventory.getItems().filter { it.itemId == itemId && it.isStackable }.sumOf { it.amount }
  }

  companion object {
    const val MAX_ENTRIES = 10
    const val MAX_AMOUNT = 99

    private val LOG = KotlinLogging.logger { }
  }
}
