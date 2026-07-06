package net.bestia.zone.ai.goal.consideration

import org.springframework.stereotype.Component

/**
 * Extracts a single normalised signal in `[0,1]` from a [DecisionContext]. Inputs are referenced by
 * the `input:` key of a consideration in a YAML archetype. An input may either match one fixed id
 * or a family of ids (see [TraitInput], which serves every `trait_*` key), so [handles] is used for
 * resolution rather than a plain id map.
 */
interface ConsiderationInput {
  fun handles(inputId: String): Boolean
  fun evaluate(inputId: String, context: DecisionContext): Double
}

/** `enemy_in_sight`: 1.0 when at least one hostile is currently perceived, else 0.0. */
@Component
class EnemyInSightInput : ConsiderationInput {
  override fun handles(inputId: String) = inputId == "enemy_in_sight"
  override fun evaluate(inputId: String, context: DecisionContext) =
    if (context.enemyInSight) 1.0 else 0.0
}

/** `own_health_pct`: the NPC's current health fraction in `[0,1]`. */
@Component
class OwnHealthPctInput : ConsiderationInput {
  override fun handles(inputId: String) = inputId == "own_health_pct"
  override fun evaluate(inputId: String, context: DecisionContext) =
    context.ownHealthPct.coerceIn(0.0, 1.0)
}

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
