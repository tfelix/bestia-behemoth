package net.bestia.zone.battle.status

/**
 * The accumulated flat and percentage bonus to **one** condition pool's regeneration rate - not to be
 * confused with [net.bestia.zone.ecs.battle.status.RegenerationModifiers], the component that carries
 * one of these per pool.
 *
 * Every contributor a character has folds into a single pair of numbers here: worn equipment, active
 * status effects and learned passive skills all call one of the `add*Regen` methods on
 * [StatusValueRecalcContext] while `StatusValueRecalcSystem` rebuilds them from scratch.
 * [RegenerationCalculator.applyModifier] then resolves the pair against a base rate.
 *
 * **Immutable on purpose.** It is reachable both from the per-tick throwaway
 * [StatusValueRecalcContext] and from the long-lived `RegenerationModifiers` component; if it were a
 * mutable accumulator, sharing an instance between the two would alias a scratch value into
 * persisted state, and the safe assignment would look identical to the unsafe one at the call site.
 * With a value type that bug is unrepresentable.
 *
 * **Percentages accumulate additively** (`+6%` then `+10%` is `+16%`, not `1.06 * 1.10`), which is
 * what the docs' single `ModPerc` term describes, keeps the whole path in integer arithmetic, and
 * makes the result independent of the order contributors are applied in.
 */
data class RegenModifier(
  val flat: Int = 0,
  val percent: Int = 0
) {

  fun plus(flat: Int = 0, percent: Int = 0) = RegenModifier(
    flat = this.flat + flat,
    percent = this.percent + percent
  )
}
