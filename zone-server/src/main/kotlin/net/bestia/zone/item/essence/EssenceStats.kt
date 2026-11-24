package net.bestia.zone.item.essence

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.item.ecs.Equipment
import org.springframework.stereotype.Component

/** What the essences in worn gear add to their wearer. */
@Component
class EssenceStats(private val species: SpeciesEssences) {

  /** Sums each effect over all worn essences first, because a cap like Swift's holds for the sum, not per gem. */
  fun applyTo(context: StatusValueRecalcContext, worn: Collection<Equipment.EquippedItem>) {
    val defined = worn.flatMap { it.essences }.mapNotNull { essence ->
      species.of(essence.bestiaId)?.let { it to essence.tier }
    }

    defined.groupBy { (definition, _) -> definition.effect to definition.target }
      .forEach { (effectAndTarget, essences) ->
        val (effect, target) = effectAndTarget
        val points = essences.sumOf { (definition, tier) -> definition.pointsIn(tier) }
        val bestTier = essences.maxOf { (_, tier) -> tier }
        effect.apply(context, EssenceSum(target, points, bestTier))
      }

    defined.mapNotNull { (definition, _) -> definition.drawback }.forEach { it.applyTo(context) }
  }
}
