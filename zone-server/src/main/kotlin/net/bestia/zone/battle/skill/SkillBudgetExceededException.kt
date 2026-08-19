package net.bestia.zone.battle.skill

import net.bestia.zone.BestiaException

/**
 * A skill script asked for more world work than one cast is allowed. Thrown out of [SkillWorld] and
 * caught by [SkillExecutionService], which fizzles the cast.
 *
 * Deliberately not recoverable by the script: a script that could catch this would keep going, and
 * the point of the budget is that it cannot.
 */
class SkillBudgetExceededException(
  reason: String
) : BestiaException(
  code = "SKILL_BUDGET_EXCEEDED",
  message = reason
)
