package net.bestia.zone.ai.perception

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.OpenStation
import net.bestia.zone.ai.domain.bestia.StationClaims
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.mining.ShaftOre
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import org.springframework.stereotype.Component

/**
 * Notices where a worker works, and which of its master's places in its areas wait for it, nearest first: stations
 * that wait for a free crafter and have work in its craft area, shafts nobody works with ore left in its mine area.
 *
 * A bestia working for its fallback job puts that work down as soon as its main job has some again.
 */
@Component
class StationSense(
  private val structures: PlayerStructureRegistry,
  private val shaftOre: ShaftOre,
  private val claims: StationClaims,
) : Sense {

  override val name = "station"

  override val intervalSeconds = 2f

  override val reads: ComponentClassSet = setOf(OwnedBestia::class)

  override fun sense(context: SenseContext) {
    val craftArea = context.recall(BestiaDomain.CRAFT_AREA)
    val mineArea = context.recall(BestiaDomain.MINE_AREA)
    if (craftArea == null && mineArea == null) return
    val owned = context.world.get(context.entityId, OwnedBestia::class) ?: return
    val masterId = owned.masterId

    val mine = structures.all().filter { it.ownerMasterId == masterId && !it.isUnderConstruction }
    val stations = craftArea?.let { area ->
      mine.filter { it.waitsForCrafter && it.queue.isNotEmpty() && area.contains(it.position) }
    }
    val shafts = mineArea?.let { area ->
      mine.filter { it.waitsForMiner && !shaftOre.isDugOut(it.id) && area.contains(it.position) }
    }
    // From the registry rather than its WorkAssignment, which lands only at the end of the tick that claimed it.
    val workplace = structures.workplaceOf(owned.playerBestiaId)?.takeUnless { worksForFallback(context, it) }

    context.remember(BestiaDomain.AT_WORK, workplace != null)
    context.remember(BestiaDomain.AT_SHAFT, workplace?.kind == StaticEntityKind.MINING_SHAFT)
    context.remember(BestiaDomain.OPEN_STATIONS, stations.orEmpty().nearestFirst(context))
    context.remember(BestiaDomain.OPEN_SHAFTS, shafts.orEmpty().nearestFirst(context))
  }

  /**
   * At the fallback job's workplace while the main job has work again, in which case it is put down here. Judged
   * from what was last seen of the main job's work.
   */
  private fun worksForFallback(context: SenseContext, workplace: StructureEntry): Boolean {
    val mainJob = context.recall(BestiaDomain.MAIN_JOB) ?: return false
    val workplaceJob = if (workplace.kind == StaticEntityKind.MINING_SHAFT) BestiaJob.MINE else BestiaJob.CRAFT
    if (workplaceJob == mainJob || BestiaDomain.mainJobIdle(context.agent.memory.snapshot())) return false

    claims.quit(context.world, context.entityId)
    return true
  }

  private fun List<StructureEntry>.nearestFirst(context: SenseContext): List<OpenStation> {
    return sortedBy { it.position.distance(context.position) }.map { OpenStation(it.id, it.position) }
  }
}
