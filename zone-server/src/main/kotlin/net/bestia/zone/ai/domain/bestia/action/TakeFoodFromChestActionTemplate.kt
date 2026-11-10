package net.bestia.zone.ai.domain.bestia.action

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
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.item.care.BagCare
import net.bestia.zone.item.container.ItemHandover
import net.bestia.zone.item.ecs.CarryCapacity
import net.bestia.zone.item.ecs.CarryCheck

/**
 * Walks to the job chest and takes a few of the foods a bestia may eat, grounding
 * [BestiaDomain.Goals.RESTOCK_FOOD]. Eating them is [BestiaDomain.Goals.EAT_FROM_BAG]'s business.
 */
class TakeFoodFromChestActionTemplate(
  private val locomotion: Locomotion,
  private val care: BagCare,
  private val handover: ItemHandover,
  private val aoi: EntityAOIService,
) : ActionTemplate {
  override val id = "takeFoodFromChest"

  override fun ground(state: WorldState): List<Action> {
    val position = state.get(BestiaDomain.POSITION) ?: return emptyList()
    val chest = state.get(BestiaDomain.JOB_CHEST) ?: return emptyList()
    if (state.get(BestiaDomain.FOOD_IN_CHEST) != true) return emptyList()

    return listOf(
      Action(
        name = "takeFoodFromChest(${chest.structureId})",
        effects = listOf(Effects.set(BestiaDomain.FOOD_IN_BAG, true)),
        cost = { chest.position.distance(position).toFloat() + 1f },
        behavior = {
          sequence {
            +MoveTo(chest.position, locomotion, arrivalRadius = ChestExchange.REACH)
            +TakeFood(chest, care, handover, aoi)
            +Wait(TAKE_SECONDS)
          }
        },
      )
    )
  }

  /** Once only: the sequence ticks it again during the pause, when the food is already on its way. */
  private class TakeFood(
    private val chest: JobChest,
    private val care: BagCare,
    private val handover: ItemHandover,
    private val aoi: EntityAOIService,
  ) : BtNode {
    private var taken = false

    override fun tick(context: BtContext): Status {
      if (taken) return Status.SUCCESS

      val exchange = ChestExchange.open(context.world, aoi, context.entityId, chest) ?: return Status.FAILURE
      var wanted = PORTIONS

      for (food in care.suppliesFor(exchange.contents, BagCare.Need.STAMINA)) {
        val amount = minOf(wanted, food.amount)
        if (!CarryCheck.fits(context.world, context.entityId, food.weight * amount)) continue

        val handed =
          handover.hand(context.world, exchange.masterId, exchange.chest, exchange.bestia, food.playerItemId, amount)
        if (handed) wanted -= amount
        if (wanted == 0) break
      }

      if (wanted == PORTIONS) return Status.FAILURE

      taken = true
      return Status.SUCCESS
    }

    override fun toString(): String = "TakeFood(${chest.structureId})"
  }

  companion object {
    /** A few meals: enough to last a while, little enough to leave the rest for the others. */
    private const val PORTIONS = 3
    private const val TAKE_SECONDS = 1f

    val READS: ComponentClassSet = setOf(CarryCapacity::class)
  }
}
