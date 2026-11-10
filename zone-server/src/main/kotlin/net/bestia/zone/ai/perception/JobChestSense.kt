package net.bestia.zone.ai.perception

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.WorkArea
import net.bestia.zone.ai.domain.bestia.JobChest
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.care.BagCare
import net.bestia.zone.item.ecs.ChestStorage
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.chest.ChestStorages
import org.springframework.stereotype.Component

/**
 * Notices a gatherer's job chest, its master's chest nearest the area: whether what it carries still fits there, and
 * whether there is food in it.
 * Read from the chest's live contents, which exist whether its prop stands in the world or not.
 */
@Component
class JobChestSense(
  private val structures: PlayerStructureRegistry,
  private val chests: ChestStorages,
  private val care: BagCare,
) : Sense {

  override val name = "jobChest"

  override val intervalSeconds = 2f

  override val reads: ComponentClassSet = setOf(OwnedBestia::class) + BagCare.READS

  override fun sense(context: SenseContext) {
    val area = context.recall(BestiaDomain.GATHER_AREA) ?: return
    val masterId = context.world.get(context.entityId, OwnedBestia::class)?.masterId ?: return
    val chest = nearestChest(masterId, area)

    if (chest == null) {
      context.forget(BestiaDomain.JOB_CHEST)
      context.forget(BestiaDomain.CHEST_HAS_ROOM)
      context.forget(BestiaDomain.GOODS_TO_HAUL)
      context.forget(BestiaDomain.FOOD_IN_CHEST)
      return
    }

    val contents = chests.contentsOf(chest.id)
    val goods = context.world.get(context.entityId, Inventory::class)?.let(care::spareGoods).orEmpty()
    val goodsThatFit = ChestStorage.whatFits(contents, goods)

    context.remember(BestiaDomain.JOB_CHEST, JobChest(chest.id, chest.position))
    context.remember(BestiaDomain.CHEST_HAS_ROOM, contents.size() < ChestStorage.SLOTS || goodsThatFit.isNotEmpty())
    context.remember(BestiaDomain.GOODS_TO_HAUL, goodsThatFit.isNotEmpty())
    context.remember(BestiaDomain.FOOD_IN_CHEST, care.hasFor(contents, BagCare.Need.STAMINA))
  }

  private fun nearestChest(masterId: Long, area: WorkArea): StructureEntry? {
    return structures.all()
      .filter { it.kind == StaticEntityKind.CHEST && it.ownerMasterId == masterId && !it.isUnderConstruction }
      .filter { it.position.distance(area.center) <= MAX_CHEST_DISTANCE }
      .minByOrNull { it.position.distance(area.center) }
  }

  private companion object {
    /** Tiles from the area's centre. Far enough for a camp beside a wood, near enough that hauling is not a trek. */
    const val MAX_CHEST_DISTANCE = 64L
  }
}
