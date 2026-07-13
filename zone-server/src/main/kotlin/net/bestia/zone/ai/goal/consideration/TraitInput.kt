package net.bestia.zone.ai.goal.consideration

import org.springframework.stereotype.Component

/**
 * `trait_<name>`: reads a value straight from the archetype's `traits` map (e.g. `trait_aggression`
 * -> `traits.aggression`). Missing traits read as 0.0, so an archetype that omits a trait simply
 * scores it as absent.
 */
@Component
class TraitInput : ConsiderationInput {
  override fun handles(inputId: String) = inputId.startsWith(PREFIX)

  override fun evaluate(inputId: String, context: DecisionContext): Double {
    val traitName = inputId.removePrefix(PREFIX)
    return (context.profile.traits[traitName] ?: 0.0).coerceIn(0.0, 1.0)
  }

  companion object {
    private const val PREFIX = "trait_"
  }
}