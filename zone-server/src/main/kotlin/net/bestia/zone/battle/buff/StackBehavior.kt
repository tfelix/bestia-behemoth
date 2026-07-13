package net.bestia.zone.battle.buff

/**
 * Determines what happens when a buff is (re-)applied to a target that may already carry
 * an active instance of the same [BuffDefinition].
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
