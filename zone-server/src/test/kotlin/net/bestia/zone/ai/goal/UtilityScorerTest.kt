package net.bestia.zone.ai.goal

import net.bestia.zone.ai.goal.consideration.ConsiderationInputRegistry
import net.bestia.zone.ai.goal.consideration.CurveRegistry
import net.bestia.zone.ai.goal.consideration.DecisionContext
import net.bestia.zone.ai.goal.consideration.EnemyInSightInput
import net.bestia.zone.ai.goal.consideration.IdentityCurve
import net.bestia.zone.ai.goal.consideration.InverseCurve
import net.bestia.zone.ai.goal.consideration.LinearRisingCurve
import net.bestia.zone.ai.goal.consideration.OwnHealthPctInput
import net.bestia.zone.ai.goal.consideration.TraitInput
import net.bestia.zone.ai.goal.goals.FleeGoal
import net.bestia.zone.ai.goal.goals.KillEnemyGoal
import net.bestia.zone.ai.goal.goals.WanderGoal
import net.bestia.zone.ai.profile.AiProfile
import net.bestia.zone.ai.profile.AiProfileDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UtilityScorerTest {

  private val curveRegistry = CurveRegistry(listOf(IdentityCurve(), InverseCurve(), LinearRisingCurve()))
  private val inputRegistry = ConsiderationInputRegistry(
    listOf(EnemyInSightInput(), OwnHealthPctInput(), TraitInput())
  )
  private val goalRegistry = GoalRegistry(listOf(KillEnemyGoal(), FleeGoal(), WanderGoal()))
  private val scorer = UtilityScorer(inputRegistry, curveRegistry, goalRegistry)

  // Mirrors resources/ai/aggressive-melee.yml.
  private val profile = AiProfile.fromDto(
    AiProfileDto(
      identifier = "aggressive_melee",
      traits = mapOf("aggression" to 0.8, "courage" to 0.6),
      goals = listOf(
        AiProfileDto.GoalDto(
          name = "kill_enemy",
          considerations = listOf(
            AiProfileDto.ConsiderationDto(input = "enemy_in_sight", curve = "identity"),
            AiProfileDto.ConsiderationDto(input = "own_health_pct", curve = "linear_rising"),
            AiProfileDto.ConsiderationDto(input = "trait_aggression", curve = "identity")
          )
        ),
        AiProfileDto.GoalDto(
          name = "flee",
          considerations = listOf(
            AiProfileDto.ConsiderationDto(input = "own_health_pct", curve = "inverse"),
            AiProfileDto.ConsiderationDto(input = "trait_courage", curve = "inverse")
          )
        ),
        AiProfileDto.GoalDto(name = "idle_wander", baseScore = 0.1)
      ),
      actions = listOf("approach_target", "melee_attack", "flee_to_safety", "wander")
    )
  )

  @Test
  fun `healthy with an enemy in sight chooses to kill`() {
    val context = DecisionContext(profile, ownHealthPct = 1.0, enemyInSight = true, nearestEnemyDistance = 3)

    val scored = scorer.selectGoal(context)

    assertEquals("kill_enemy", scored?.goal?.name)
  }

  @Test
  fun `low health with an enemy in sight flips the goal to flee`() {
    val context = DecisionContext(profile, ownHealthPct = 0.2, enemyInSight = true, nearestEnemyDistance = 3)

    val scored = scorer.selectGoal(context)

    assertEquals("flee", scored?.goal?.name)
  }

  @Test
  fun `idle wander wins when nothing else scores`() {
    val context = DecisionContext(profile, ownHealthPct = 1.0, enemyInSight = false, nearestEnemyDistance = null)

    val scored = scorer.selectGoal(context)

    assertEquals("idle_wander", scored?.goal?.name)
  }

  @Test
  fun `a profile with no goals selects nothing`() {
    val empty = profile.copy(goals = emptyList())
    val context = DecisionContext(empty, ownHealthPct = 1.0, enemyInSight = true, nearestEnemyDistance = 1)

    assertNull(scorer.selectGoal(context))
  }
}
