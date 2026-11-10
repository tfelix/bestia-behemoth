package net.bestia.zone.prop

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * Tending a campfire: one log at a time, by its owner, from next to it.
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
  private val clock: Clock = Clock.systemUTC(),
) {

  /** @return the refusal to send back, or null when the log went on */
  fun addWood(world: World, actorId: EntityId, structureId: Long): OpError? {
    val now = clock.instant()
    val fire = structures.of(structureId)
      ?.takeIf { it.kind == StaticEntityKind.CAMPFIRE && !it.isUnderConstruction && !it.isGoneAt(now) }
      ?: return OpError.CRAFT_NOT_POSSIBLE

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

  private companion object {
    const val LOG_IDENTIFIER = "log"

    val LOG = KotlinLogging.logger { }
  }
}
