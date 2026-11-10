package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.ai.bt.leaves.Wait
import net.bestia.zone.ai.bt.sequence
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.StateKey
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.item.care.BagCare

/**
 * Uses one item from the bestia's own bag for [need], grounding [BestiaDomain.Goals.HEAL_FROM_BAG] or
 * [BestiaDomain.Goals.EAT_FROM_BAG]. Plans as if it restores the vital fully; perception reads the real value
 * back, and if one item was not enough the goal comes round again.
 */
class UseFromBagActionTemplate private constructor(
  override val id: String,
  private val care: BagCare,
  private val need: BagCare.Need,
  private val inBag: StateKey<Boolean>,
  private val vital: StateKey<Int>,
) : ActionTemplate {

  override fun ground(state: WorldState): List<Action> {
    if (state.get(inBag) != true) return emptyList()

    return listOf(
      Action(
        name = id,
        effects = listOf(Effects.set(vital, 100)),
        cost = { 1f },
        // The pause is the meal itself, and keeps a bestia that needs two from swallowing both in one tick.
        behavior = { sequence { +UseFromBag(care, need); +Wait(USE_SECONDS) } },
      )
    )
  }

  private class UseFromBag(private val care: BagCare, private val need: BagCare.Need) : BtNode {
    override fun tick(context: BtContext): Status =
      if (care.use(context.world, context.entityId, need)) Status.SUCCESS else Status.FAILURE

    override fun toString(): String = "UseFromBag($need)"
  }

  companion object {
    private const val USE_SECONDS = 2f

    fun heal(care: BagCare) =
      UseFromBagActionTemplate("healFromBag", care, BagCare.Need.HEALTH, BestiaDomain.HEAL_IN_BAG, BestiaDomain.HEALTH_PCT)

    fun eat(care: BagCare) =
      UseFromBagActionTemplate("eatFromBag", care, BagCare.Need.STAMINA, BestiaDomain.FOOD_IN_BAG, BestiaDomain.STAMINA_PCT)
  }
}
