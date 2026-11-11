package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.bestia.diary.BestiaNews
import net.bestia.zone.bestia.diary.BestiaHappened
import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.bt.leaves.MoveTo
import net.bestia.zone.ai.bt.leaves.Wait
import net.bestia.zone.ai.bt.sequence
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.JobChest
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.item.care.BagCare
import net.bestia.zone.item.container.ItemHandover
import net.bestia.zone.item.ecs.ChestStorage

/**
 * Carries what a gatherer collected to its job chest and puts in all that fits, grounding
 * [BestiaDomain.Goals.HAUL_TO_CHEST]. Its own care items stay in its bag.
 */
class HaulToChestActionTemplate(
  private val locomotion: Locomotion,
  private val care: BagCare,
  private val handover: ItemHandover,
  private val aoi: EntityAOIService,
  private val news: BestiaNews,
  private val identifierOf: (Long) -> String?,
) : ActionTemplate {
  override val id = "haulToChest"

  override fun ground(state: WorldState): List<Action> {
    val position = state.get(BestiaDomain.POSITION) ?: return emptyList()
    val chest = state.get(BestiaDomain.JOB_CHEST) ?: return emptyList()
    if (state.get(BestiaDomain.GOODS_TO_HAUL) != true) return emptyList()

    return listOf(
      Action(
        name = "haulToChest(${chest.structureId})",
        effects = listOf(Effects.set(BestiaDomain.GOODS_TO_HAUL, false)),
        cost = { chest.position.distance(position).toFloat() + 1f },
        behavior = {
          sequence {
            +MoveTo(chest.position, locomotion, arrivalRadius = ChestExchange.REACH)
            +Deposit(chest, care, handover, aoi, news, identifierOf)
            +Wait(DEPOSIT_SECONDS)
          }
        },
      )
    )
  }

  /** Once only: the sequence ticks it again during the pause, when the goods are already on their way. */
  private class Deposit(
    private val chest: JobChest,
    private val care: BagCare,
    private val handover: ItemHandover,
    private val aoi: EntityAOIService,
    private val news: BestiaNews,
    private val identifierOf: (Long) -> String?,
  ) : BtNode {
    private var deposited = false

    override fun tick(context: BtContext): Status {
      if (deposited) return Status.SUCCESS

      val exchange = ChestExchange.open(context.world, aoi, context.entityId, chest) ?: return Status.FAILURE
      val deposit = ChestStorage.whatFits(exchange.contents, care.spareGoods(exchange.bag))
      // Summed first: handing a pile over empties it.
      val hauled = deposit.groupBy { it.itemId }.mapValues { (_, piles) -> piles.sumOf { it.amount } }
      deposit.forEach { item ->
        handover.hand(context.world, exchange.masterId, exchange.bestia, exchange.chest, item.playerItemId, item.amount)
      }
      tellHauled(context, exchange.masterId, hauled)

      deposited = true
      return Status.SUCCESS
    }

    /** One diary line per haul, summing the goods, rather than one per pile. */
    private fun tellHauled(context: BtContext, masterId: Long, hauled: Map<Long, Int>) {
      val playerBestiaId = context.world.get(context.entityId, OwnedBestia::class)?.playerBestiaId ?: return
      val items = hauled.mapNotNull { (itemId, amount) -> identifierOf(itemId)?.let { it to amount } }.toMap()
      if (items.isEmpty()) return

      news.tell(BestiaHappened(this, masterId, playerBestiaId, BestiaHappened.Kind.HAULED, items = items))
    }

    override fun toString(): String = "Deposit(${chest.structureId})"
  }

  companion object {
    private const val DEPOSIT_SECONDS = 1f
  }
}
