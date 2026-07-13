package net.bestia.zone.ai.goal.consideration

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
