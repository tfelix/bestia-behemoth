package net.bestia.zone.ai.perception

import net.bestia.zone.ai.domain.bestia.PlaceLookup
import net.bestia.zone.bestia.diary.BestiaNews
import net.bestia.zone.bestia.diary.BestiaHappened
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.JobChest
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.care.BagCare
import net.bestia.zone.item.ecs.ChestStorage
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.chest.ChestStorages
import net.bestia.zone.prop.station.WorkAssignment
import org.springframework.stereotype.Component

/**
 * Notices a gatherer's or crafter's job chest, its master's chest nearest the area, or nearest the station it works:
 * whether what it carries still fits there, and whether there is food in it.
 * Read from the chest's live contents, which exist whether its prop stands in the world or not.
 */
@Component
class JobChestSense(
  private val structures: PlayerStructureRegistry,
  private val chests: ChestStorages,
  private val care: BagCare,
  private val news: BestiaNews = BestiaNews { },
  private val places: PlaceLookup = PlaceLookup { null },
) : Sense {

  override val name = "jobChest"

  override val intervalSeconds = 2f

  override val reads: ComponentClassSet = setOf(OwnedBestia::class, WorkAssignment::class) + BagCare.READS

  override fun sense(context: SenseContext) {
    val area = context.recall(BestiaDomain.GATHER_AREA)
      ?: context.recall(BestiaDomain.CRAFT_AREA)
      ?: context.recall(BestiaDomain.MINE_AREA)
      ?: return
    val masterId = context.world.get(context.entityId, OwnedBestia::class)?.masterId ?: return
    // At a station, the chest nearest it is the one that pays its queue.
    val workplace = context.world.get(context.entityId, WorkAssignment::class)?.position
    val chest = namedChest(context, masterId) ?: nearestChest(masterId, workplace ?: area.center)

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

    tellWhenFull(context, goods.isNotEmpty() && goodsThatFit.isEmpty())

    context.remember(BestiaDomain.JOB_CHEST, JobChest(chest.id, chest.position))
    context.remember(BestiaDomain.CHEST_HAS_ROOM, contents.size() < ChestStorage.SLOTS || goodsThatFit.isNotEmpty())
    context.remember(BestiaDomain.GOODS_TO_HAUL, goodsThatFit.isNotEmpty())
    context.remember(BestiaDomain.FOOD_IN_CHEST, care.hasFor(contents, BagCare.Need.STAMINA))
  }

  /** Once per stretch of being full: a bestia whose chest takes nothing more has stopped working. */
  private fun tellWhenFull(context: SenseContext, full: Boolean) {
    val told = context.recall(BestiaDomain.CHEST_FULL_TOLD) == true
    if (full == told) return

    context.remember(BestiaDomain.CHEST_FULL_TOLD, full)
    if (!full) return
    val owned = context.world.get(context.entityId, OwnedBestia::class) ?: return
    news.tell(
      BestiaHappened(this, owned.masterId, owned.playerBestiaId, BestiaHappened.Kind.WORK_PAUSED, reason = CHEST_FULL)
    )
  }

  /** The chest the orders name, while it stands and is still the master's. */
  private fun namedChest(context: SenseContext, masterId: Long): StructureEntry? {
    val placeId = context.recall(BestiaDomain.CHEST_PLACE_ID) ?: return null
    val structureId = places.placeOf(placeId)?.chestStructureId ?: return null

    return structures.of(structureId)?.takeIf { it.ownerMasterId == masterId && !it.isUnderConstruction }
  }

  private fun nearestChest(masterId: Long, to: Vec3L): StructureEntry? {
    return structures.all()
      .filter { it.kind == StaticEntityKind.CHEST && it.ownerMasterId == masterId && !it.isUnderConstruction }
      .filter { it.position.distance(to) <= MAX_CHEST_DISTANCE }
      .minByOrNull { it.position.distance(to) }
  }

  private companion object {
    /** Why a worker stopped, in its diary. */
    const val CHEST_FULL = "CHEST_FULL"

    /**
     * Tiles from the area's centre or the station. Far enough for a camp beside a wood, near enough that hauling is
     * not a trek.
     */
    const val MAX_CHEST_DISTANCE = 64L
  }
}
