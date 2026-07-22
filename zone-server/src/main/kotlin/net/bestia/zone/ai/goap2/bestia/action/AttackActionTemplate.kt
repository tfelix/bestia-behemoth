package net.bestia.zone.ai.goap2.bestia.action

import net.bestia.zone.ai.goap2.action.Action
import net.bestia.zone.ai.goap2.action.ActionTemplate
import net.bestia.zone.ai.goap2.bestia.AttackDefinition
import net.bestia.zone.ai.goap2.bestia.AttackEffectiveness
import net.bestia.zone.ai.goap2.bestia.BestiaDomain
import net.bestia.zone.ai.goap2.bestia.EffectivenessKey
import net.bestia.zone.ai.goap2.effect.Effects
import net.bestia.zone.ai.goap2.state.WorldState

/**
 * Grounds one attack action per known [attacks] entry currently in range. All of them lead to the
 * same [BestiaDomain.TARGET_DEAD] outcome, so which one the planner actually picks comes down purely
 * to [Action.cost] — and that cost is scaled by [BestiaDomain.ATTACK_EFFECTIVENESS] (cheaper the more
 * effective it's remembered to be, a neutral [AttackEffectiveness.UNKNOWN_ESTIMATE] for anything never
 * tried against this archetype). This is what "pick the most suitable attack, try attacks out,
 * remember what works well" reduces to: no separate decision mechanism needed, A* already picks the
 * cheapest path.
 */
class AttackActionTemplate(private val attacks: List<AttackDefinition>) : ActionTemplate {
  override val id = "attack"

  override fun ground(state: WorldState): List<Action> {
    val position = state.get(BestiaDomain.POSITION) ?: return emptyList()
    val targetPosition = state.get(BestiaDomain.TARGET_POSITION) ?: return emptyList()
    val archetype = state.get(BestiaDomain.TARGET_ARCHETYPE) ?: UNKNOWN_ARCHETYPE
    val distance = position.distance(targetPosition)
    val effectiveness = state.get(BestiaDomain.ATTACK_EFFECTIVENESS) ?: emptyMap()

    return attacks
      .filter { distance <= it.range }
      .map { attack ->
        val estimate = effectiveness[EffectivenessKey(archetype, attack.id)] ?: AttackEffectiveness.UNKNOWN_ESTIMATE
        Action(
          name = "attack(${attack.id})",
          effects = listOf(Effects.set(BestiaDomain.TARGET_DEAD, true)),
          cost = { attack.baseCost * (1.5f - estimate.toFloat()) },
        )
      }
  }

  companion object {
    private const val UNKNOWN_ARCHETYPE = "unknown"
  }
}
