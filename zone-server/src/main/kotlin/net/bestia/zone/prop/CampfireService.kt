package net.bestia.zone.prop

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ItemHolder
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.chest.ChestStorages
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * Tending a campfire: one log at a time, by its owner from next to it, or by the bestia working it from the chest
 * next to it.
 *
 * The log comes off the live inventory now and out of the database afterwards, the order crafting uses - see
 * `CraftingService` for why the tick thread never waits on the database.
 */
@Service
class CampfireService(
  private val structures: PlayerStructureRegistry,
  private val itemTemplates: ItemTemplateRegistry,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val chests: ChestStorages,
  private val clock: Clock = Clock.systemUTC(),
) {

  /** @return the refusal to send back, or null when the log went on */
  fun addWood(world: World, actorId: EntityId, structureId: Long): OpError? {
    val now = clock.instant()
    val fire = tendable(structureId, now) ?: return OpError.CRAFT_NOT_POSSIBLE

    val master = world.get(actorId, Master::class)
    val at = world.get(actorId, Position::class)?.toVec3L()
    if (master?.masterId != fire.ownerMasterId || at == null || at.distance(fire.position) > PlayerStructureService.RANGE_TILES) {
      return OpError.CRAFT_NOT_POSSIBLE
    }

    // Refused rather than spending the log for nothing: the fire already holds all it can.
    val burnsOutAt = Campfire.withLog(fire.burnsOutAt ?: now, now) ?: return OpError.CRAFT_NOT_POSSIBLE

    val log = itemTemplates.idOf(LOG_IDENTIFIER) ?: return OpError.CRAFT_NOT_POSSIBLE
    val taken = world.get(actorId, Inventory::class)?.takeAcrossPiles(log, 1) ?: return OpError.CRAFT_MISSING_MATERIALS

    structures.setBurnsOutAt(structureId, burnsOutAt)

    asyncJobExecutor.submit(master.masterId) {
      if (!inventoryService.consumeAll(master.masterId, listOf(log to 1))) {
        LOG.warn { "Master ${master.masterId} lost the log for campfire $structureId in the database; giving it back" }
        world.modify(actorId) { id -> taken.forEach { get(id, Inventory::class)?.addItem(it) } }
      }
    }

    return null
  }

  /**
   * The bestia working [structureId] puts a log from the chest next to it on the fire, by the rules a player adds
   * wood by. Only once the fire runs low, so the chest's wood lasts.
   *
   * @return whether a log went on
   */
  fun addWoodFromChest(world: World, structureId: Long): Boolean {
    val now = clock.instant()
    val fire = tendable(structureId, now)?.takeIf { Campfire.runsLowAt(it, now) } ?: return false
    val burnsOutAt = Campfire.withLog(fire.burnsOutAt ?: now, now) ?: return false

    val chest = structures.chestBeside(fire) ?: return false
    val log = itemTemplates.idOf(LOG_IDENTIFIER) ?: return false
    val contents = chests.contentsOf(chest.id)
    val taken = contents.takeAcrossPiles(log, 1) ?: return false

    structures.setBurnsOutAt(structureId, burnsOutAt)

    val masterId = fire.ownerMasterId
    asyncJobExecutor.submit(masterId) {
      if (!inventoryService.consumeAllFrom(masterId, mapOf(ItemHolder.Chest(chest.id) to mapOf(log to 1)))) {
        LOG.warn { "Chest ${chest.id} lost the log for campfire $structureId in the database; giving it back" }
        world.post { contents.addItems(taken) }
      }
    }

    return true
  }

  private fun tendable(structureId: Long, now: Instant): StructureEntry? {
    return structures.of(structureId)
      ?.takeIf { it.kind == StaticEntityKind.CAMPFIRE && !it.isUnderConstruction && !it.isGoneAt(now) }
  }

  private companion object {
    const val LOG_IDENTIFIER = "log"

    val LOG = KotlinLogging.logger { }
  }
}
