package net.bestia.zone.ai.goap2.bestia

import net.bestia.zone.ai.goap2.state.Blackboard

/**
 * Species-wide "did this attack work?" learning. This is deliberately *not* part of the pure
 * planning loop: [net.bestia.zone.ai.goap2.bestia.action.AttackActionTemplate] only *reads*
 * [BestiaDomain.ATTACK_EFFECTIVENESS] to prefer cheaper (= remembered-effective) attacks over
 * unproven or confirmed-poor ones. Something outside goap2 that actually resolves combat and knows
 * the real outcome (e.g. damage dealt / target max health) calls [record] with it — real damage
 * resolution is still a placeholder elsewhere in this codebase (`BaseDamageCalculator`), so today
 * that caller is a test harness simulating an outcome, not the live game loop.
 */
object AttackEffectiveness {

  /** Effectiveness assumed for an attack never yet tried against this archetype. */
  const val UNKNOWN_ESTIMATE = 0.5

  /**
   * Nudges the remembered effectiveness of [key] towards [observed] (both in `0.0..1.0`) by an
   * exponential moving average, so one lucky/unlucky hit doesn't overwrite a longer track record.
   */
  fun record(memory: Blackboard, key: EffectivenessKey, observed: Double, learningRate: Double = 0.3) {
    val current = memory.get(BestiaDomain.ATTACK_EFFECTIVENESS) ?: emptyMap()
    val prior = current[key] ?: UNKNOWN_ESTIMATE
    val updated = (prior + learningRate * (observed - prior)).coerceIn(0.0, 1.0)
    memory.set(BestiaDomain.ATTACK_EFFECTIVENESS, current + (key to updated), Blackboard.PERMANENT)
  }
}
