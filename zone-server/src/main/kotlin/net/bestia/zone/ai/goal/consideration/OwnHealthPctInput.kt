package net.bestia.zone.ai.goal.consideration

import org.springframework.stereotype.Component

/** `own_health_pct`: the NPC's current health fraction in `[0,1]`. */
@Component
class OwnHealthPctInput : ConsiderationInput {
  override fun handles(inputId: String) = inputId == "own_health_pct"
  override fun evaluate(inputId: String, context: DecisionContext) =
    context.ownHealthPct.coerceIn(0.0, 1.0)
}