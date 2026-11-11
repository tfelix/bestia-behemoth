package net.bestia.zone.companion.query

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneAnswer.Workshop.AffordableRecipe
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.crafting.CraftingService
import net.bestia.zone.crafting.Crafters
import net.bestia.zone.crafting.RecipeEffect
import net.bestia.zone.crafting.RecipeRegistry
import net.bestia.zone.crafting.station.CrafterProfiles
import net.bestia.zone.crafting.station.StationPause
import net.bestia.zone.crafting.station.StationQueueService
import net.bestia.zone.crafting.station.StationWork
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.message.AccountTaskExecutor
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.chest.ChestStorages
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

/**
 * The master's crafting stations, from the companion app: their queues and workers, and queueing, cancelling,
 * reordering and picking the worker from afar. Ingredients come only from the chest beside a station, never from the
 * master's bag. Writes run in the account's inbox, so they are ordered with what the game client sends.
 */
@Service
class WorkshopCommands(
  private val owned: OwnedMasters,
  private val world: WorldView,
  private val structures: PlayerStructureRegistry,
  private val recipes: RecipeRegistry,
  private val crafting: CraftingService,
  private val profiles: CrafterProfiles,
  private val queues: StationQueueService,
  private val work: StationWork,
  private val chests: ChestStorages,
  private val items: ItemTemplateRegistry,
  private val playerBestias: PlayerBestiaRepository,
  private val plane: MapPlane,
  private val inbox: AccountTaskExecutor,
) {

  fun list(correlationId: String, accountId: Long, query: ZoneQuery.Workshop): BusReply {
    owned.find(accountId, query.masterId) ?: return notFound(correlationId)

    return BusReply.ok(correlationId, workshopOf(query.masterId))
  }

  fun queue(correlationId: String, accountId: Long, query: ZoneQuery.QueueAtStation): BusReply {
    return write(correlationId, accountId, query.masterId) {
      queues.enqueueFromChest(this, query.masterId, query.structureId, query.recipeId, query.amount)?.name
    }
  }

  fun cancel(correlationId: String, accountId: Long, query: ZoneQuery.CancelAtStation): BusReply {
    return write(correlationId, accountId, query.masterId) {
      queues.cancelFromAfar(this, query.masterId, query.structureId, query.index)?.name
    }
  }

  fun move(correlationId: String, accountId: Long, query: ZoneQuery.MoveAtStation): BusReply {
    return write(correlationId, accountId, query.masterId) {
      queues.moveFromAfar(query.masterId, query.structureId, query.from, query.to)?.name
    }
  }

  fun setWorker(correlationId: String, accountId: Long, query: ZoneQuery.SetStationWorker): BusReply {
    // Read before the world lease: the database must not be touched while the tick waits.
    val workerEntityId = query.playerBestiaId?.let { id ->
      playerBestias.findByIdOrNull(id)?.takeIf { it.master.id == query.masterId }?.entityId
        ?: return BusReply.of(correlationId, Status.INVALID, "That bestia is not in the world")
    }

    return write(correlationId, accountId, query.masterId) {
      queues.setWorkerFromAfar(this, query.masterId, query.structureId, workerEntityId, query.anyFreeCrafter)?.name
    }
  }

  /** Runs [change] on the world for an owned master; it answers a refusal, or null when it went through. */
  private fun write(correlationId: String, accountId: Long, masterId: Long, change: World.() -> String?): BusReply {
    var reply = notFound(correlationId)
    inbox.onIo(accountId) {
      // Never let this throw: a failure in an account's inbox closes that account's game connection.
      reply = try {
        if (owned.find(accountId, masterId) == null) {
          notFound(correlationId)
        } else {
          val refusal = world.read(change)
          if (refusal == null) BusReply.ok(correlationId, workshopOf(masterId))
          else BusReply.of(correlationId, Status.INVALID, refusal)
        }
      } catch (e: Exception) {
        LOG.error(e) { "A workshop order of account $accountId failed" }
        BusReply.of(correlationId, Status.BUSY, "internal error")
      }
    }.join()

    return reply
  }

  private fun workshopOf(masterId: Long): ZoneAnswer.Workshop {
    val crafter = profiles.knowledgeOf(masterId)
    val stationKinds = recipes.stations().toSet()

    return world.read {
      val stations = structures.all()
        .filter { it.ownerMasterId == masterId && it.kind in stationKinds && !it.isUnderConstruction }
        .sortedBy { it.id }
      ZoneAnswer.Workshop(stations.map { stationOf(it, crafter) })
    }
  }

  private fun stationOf(station: StructureEntry, crafter: Crafters.Crafter?): ZoneAnswer.Workshop.StationView {
    val chest = structures.chestBeside(station)
    val worker = when {
      station.workerBestiaId != null && !station.takesAnyCrafter -> "BESTIA"
      station.takesAnyCrafter -> "ANY_FREE_CRAFTER"
      else -> "OWNER"
    }

    return ZoneAnswer.Workshop.StationView(
      id = station.id,
      kind = station.kind.name,
      eastMetres = plane.eastMetres(station.position),
      northMetres = plane.northMetres(station.position),
      queue = station.queue.map { entry ->
        val output = recipes.of(entry.recipeId)?.output
        ZoneAnswer.Workshop.QueueLine(entry.recipeId, output?.let { items.identifierOf(it.itemId) }, entry.amount, entry.done)
      },
      worker = worker,
      workerBestiaId = station.workerBestiaId,
      pause = work.peek(station.id)?.pause?.takeIf { it != StationPause.NONE }?.name,
      hasChest = chest != null,
      affordable = if (chest != null && crafter != null) affordable(station, crafter, chests.contentsOf(chest.id)) else emptyList(),
    )
  }

  /** The recipes of [station] the master may queue and the chest can pay for at least once, and how often. */
  private fun affordable(station: StructureEntry, crafter: Crafters.Crafter, chest: Inventory): List<AffordableRecipe> {
    return recipes.forStation(station.kind)
      .filter { it.effect == RecipeEffect.PRODUCE && crafting.refusalToQueue(crafter, it) == null }
      .mapNotNull { recipe ->
        val item = recipe.output?.let { items.identifierOf(it.itemId) } ?: return@mapNotNull null
        val most = recipe.inputTotals.minOfOrNull { (itemId, each) -> stackedAmount(chest, itemId) / each }
          ?: StationQueueService.MAX_AMOUNT
        AffordableRecipe(recipe.id, item, most.coerceAtMost(StationQueueService.MAX_AMOUNT)).takeIf { most > 0 }
      }
  }

  /** Stacks only, as a queue pays: the database can only take from stacks. */
  private fun stackedAmount(chest: Inventory, itemId: Long): Int {
    return chest.getItems().filter { it.itemId == itemId && it.isStackable }.sumOf { it.amount }
  }

  private fun notFound(correlationId: String) = BusReply.of(correlationId, Status.NOT_FOUND)

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
