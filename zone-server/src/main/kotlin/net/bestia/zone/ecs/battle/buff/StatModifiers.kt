package net.bestia.zone.ecs.battle.buff

import net.bestia.zone.battle.buff.ModifierMode
import net.bestia.zone.battle.status.StatType
import net.bestia.zone.ecs.core.Component

/**
 * Aggregated additive/multiplicative buff modifiers per [StatType], recomputed each tick from an
 * entity's [Buffs] by [StatAggregationSystem]. Internal bookkeeping only - deliberately not
 * [net.bestia.zone.ecs.Dirtyable], the client never sees this directly.
 *
 * The extensibility seam for stat-modifying buffs: a future buffable stat is a new [StatType]
 * member plus one small consumer system reading [effective], not a redesign of this component.
 */
class StatModifiers : Component {
  private val additive = mutableMapOf<StatType, Double>()
  private val multiplicative = mutableMapOf<StatType, Double>()

  fun clear() {
    additive.clear()
    multiplicative.clear()
  }

  fun addModifier(stat: StatType, mode: ModifierMode, value: Double) {
    val target = when (mode) {
      ModifierMode.ADDITIVE -> additive
      ModifierMode.MULTIPLICATIVE -> multiplicative
    }
    target.merge(stat, value, Double::plus)
  }

  /** Applies all aggregated modifiers for [stat] to [base]: additive first, then multiplicative. */
  fun effective(base: Float, stat: StatType): Float {
    val flat = additive[stat] ?: 0.0
    val pct = multiplicative[stat] ?: 0.0
    return ((base + flat) * (1.0 + pct)).toFloat()
  }
}
