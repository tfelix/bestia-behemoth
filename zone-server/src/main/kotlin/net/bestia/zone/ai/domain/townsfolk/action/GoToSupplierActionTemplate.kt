package net.bestia.zone.ai.domain.townsfolk.action

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.bt.leaves.MoveTo
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain

/** Walks to whoever makes the goods. [GoToMealActionTemplate]'s twin, with the same grounds-from-away rule. */
class GoToSupplierActionTemplate(private val locomotion: Locomotion) : ActionTemplate {
  override val id = "goToSupplier"

  override fun ground(state: WorldState): List<Action> {
    val supplier = state.get(TownsfolkDomain.SUPPLIER_POSITION) ?: return emptyList()
    val position = state.get(TownsfolkDomain.POSITION) ?: return emptyList()
    if (position.distance(supplier) <= TownsfolkDomain.DOORSTEP_RADIUS) return emptyList()

    return listOf(
      Action(
        name = "goToSupplier",
        effects = listOf(Effects.set(TownsfolkDomain.POSITION, supplier)),
        cost = { s -> (s.get(TownsfolkDomain.POSITION)?.distance(supplier) ?: Long.MAX_VALUE).toFloat() },
        behavior = { MoveTo(supplier, locomotion, arrivalRadius = TownsfolkDomain.DOORSTEP_RADIUS) },
      )
    )
  }
}
