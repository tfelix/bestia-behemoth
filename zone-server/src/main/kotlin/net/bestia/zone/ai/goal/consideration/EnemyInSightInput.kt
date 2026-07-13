package net.bestia.zone.ai.goal.consideration

import org.springframework.stereotype.Component

/** `enemy_in_sight`: 1.0 when at least one hostile is currently perceived, else 0.0. */
@Component
class EnemyInSightInput : ConsiderationInput {
  override fun handles(inputId: String) = inputId == "enemy_in_sight"
  override fun evaluate(inputId: String, context: DecisionContext) =
    if (context.enemyInSight) 1.0 else 0.0
}