package net.bestia.zone.battle.buff

/** How a [StatusEffectEffect.StatModifierEffect] combines its value with a stat's base value. */
enum class ModifierMode {
  /** Summed with other additive modifiers, then added to the base value. */
  ADDITIVE,

  /** Summed with other multiplicative modifiers, then applied as `(1 + sum)` against the base value. */
  MULTIPLICATIVE
}
