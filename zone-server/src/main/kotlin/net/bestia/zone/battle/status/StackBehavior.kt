package net.bestia.zone.battle.status

/**
 * Determines what happens when a status effect is (re-)applied to a target that may already carry
 * an active instance of the same [StatusEffectDefinition]. Owned by the effect's
 * [StatusEffectScript], not the definition - mirrors rAthena, where stacking rules are hardcoded
 * per status change type in code, not data.
 */
enum class StackBehavior {
  /** Only one instance may be active; re-applying resets its remaining duration. */
  REFRESH_DURATION,

  /** Multiple independent instances may be active at once (e.g. a stacking poison). */
  STACK_INDEPENDENT,

  /** Re-applying while an instance is already active is a no-op. */
  IGNORE_IF_PRESENT,

  /** Re-applying replaces the existing instance only if the new level is higher. */
  REPLACE_IF_STRONGER
}
