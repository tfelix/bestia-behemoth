package net.bestia.zone.ai.perception

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.OpenStation
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.mining.ShaftOre
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.station.WorkAssignment
import org.springframework.stereotype.Component

/**
 * Notices whether a worker works a station, and which of its master's places in its area wait for it, nearest first:
 * stations that wait for a free crafter and have work for a bestia under CRAFT orders, shafts nobody works with ore
 * left for one under MINE orders. Only those two jobs have such an area.
 */
@Component
class StationSense(
  private val structures: PlayerStructureRegistry,
  private val shaftOre: ShaftOre,
) : Sense {

  override val name = "station"

  override val intervalSeconds = 2f

  override val reads: ComponentClassSet = setOf(OwnedBestia::class, WorkAssignment::class)

  override fun sense(context: SenseContext) {
    val craftArea = context.recall(BestiaDomain.CRAFT_AREA)
    val mineArea = context.recall(BestiaDomain.MINE_AREA)
    val area = craftArea ?: mineArea ?: return
    val masterId = context.world.get(context.entityId, OwnedBestia::class)?.masterId ?: return

    val waiting: (StructureEntry) -> Boolean = if (craftArea != null) {
      { it.waitsForCrafter && it.queue.isNotEmpty() }
    } else {
      { it.waitsForMiner && !shaftOre.isDugOut(it.id) }
    }

    val open = structures.all()
      .filter { it.ownerMasterId == masterId && waiting(it) }
      .filter { !it.isUnderConstruction && area.contains(it.position) }
      .sortedBy { it.position.distance(context.position) }
      .map { OpenStation(it.id, it.position) }

    context.remember(BestiaDomain.AT_WORK, context.world.has(context.entityId, WorkAssignment::class))
    context.remember(BestiaDomain.OPEN_STATIONS, open)
  }
}
