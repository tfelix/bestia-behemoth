package net.bestia.zone.ai.goal

import net.bestia.zone.ai.goal.consideration.ConsiderationInputRegistry
import net.bestia.zone.ai.goal.consideration.CurveRegistry
import net.bestia.zone.ai.goal.consideration.DecisionContext
import net.bestia.zone.ai.profile.AiProfile
import net.bestia.zone.ai.profile.AiProfileDto
import org.springframework.stereotype.Service

/**
 * Scores every goal declared by an archetype against the current [DecisionContext] and returns the
 * winner. Each goal's score is `combine(weightᵢ · curveᵢ(inputᵢ))`, or its `base_score` when it has
 * no considerations. The highest score wins; ties break by declaration order (a stable max).
 */
@Service
class UtilityScorer(
  private val inputRegistry: ConsiderationInputRegistry,
  private val curveRegistry: CurveRegistry,
  private val goalRegistry: GoalRegistry
) {

  data class ScoredGoal(val goal: Goal, val spec: AiProfile.GoalSpec, val score: Double)

  /**
   * Returns the best-scoring goal for [context], or null if the profile declares no goals or all
   * score zero.
   */
  fun selectGoal(context: DecisionContext): ScoredGoal? {
    val profile = context.profile

    val best = profile.goals
      .map { spec -> ScoredGoal(goalRegistry.get(spec.name), spec, score(spec, context)) }
      .filter { it.score > 0.0 }
      // maxByOrNull keeps the first on ties, i.e. declaration order.
      .maxByOrNull { it.score }

    return best
  }

  private fun score(spec: AiProfile.GoalSpec, context: DecisionContext): Double {
    if (spec.considerations.isEmpty()) {
      return spec.baseScore
    }

    val values = spec.considerations.map { consideration ->
      val raw = inputRegistry.resolve(consideration.input).evaluate(consideration.input, context)
      val curved = curveRegistry.get(consideration.curve).apply(raw)
      consideration.weight * curved
    }

    val combined = when (spec.combine) {
      AiProfileDto.Combine.PRODUCT -> values.fold(1.0) { acc, v -> acc * v }
      AiProfileDto.Combine.MIN -> values.min()
      AiProfileDto.Combine.MAX -> values.max()
      AiProfileDto.Combine.AVERAGE -> values.average()
    }

    return maxOf(combined, spec.baseScore)
  }
}
