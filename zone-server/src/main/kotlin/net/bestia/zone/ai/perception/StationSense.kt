package net.bestia.zone.ai.perception

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.OpenStation
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.station.WorkAssignment
import org.springframework.stereotype.Component

/**
 * Notices whether a crafter works a station, and which of its master's stations in its area wait for a free
 * crafter and have work, nearest first. Only a bestia under CRAFT orders has that area.
 */
@Component
class StationSense(
  private val structures: PlayerStructureRegistry,
) : Sense {

  override val name = "station"

  override val intervalSeconds = 2f

  override val reads: ComponentClassSet = setOf(OwnedBestia::class, WorkAssignment::class)

  override fun sense(context: SenseContext) {
    val area = context.recall(BestiaDomain.CRAFT_AREA) ?: return
    val masterId = context.world.get(context.entityId, OwnedBestia::class)?.masterId ?: return

    val open = structures.all()
      .filter { it.ownerMasterId == masterId && it.waitsForCrafter && it.queue.isNotEmpty() }
      .filter { !it.isUnderConstruction && area.contains(it.position) }
      .sortedBy { it.position.distance(context.position) }
      .map { OpenStation(it.id, it.position) }

    context.remember(BestiaDomain.AT_WORK, context.world.has(context.entityId, WorkAssignment::class))
    context.remember(BestiaDomain.OPEN_STATIONS, open)
  }
}
