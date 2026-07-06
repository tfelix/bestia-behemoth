package net.bestia.zone.ai.profile

import net.bestia.zone.ai.goal.GoalRegistry
import net.bestia.zone.ai.goal.consideration.ConsiderationInputRegistry
import net.bestia.zone.ai.goal.consideration.CurveRegistry
import net.bestia.zone.ai.goal.consideration.EnemyInSightInput
import net.bestia.zone.ai.goal.consideration.IdentityCurve
import net.bestia.zone.ai.goal.consideration.InverseCurve
import net.bestia.zone.ai.goal.consideration.LinearRisingCurve
import net.bestia.zone.ai.goal.consideration.OwnHealthPctInput
import net.bestia.zone.ai.goal.consideration.TraitInput
import net.bestia.zone.ai.goal.goals.FleeGoal
import net.bestia.zone.ai.goal.goals.KillEnemyGoal
import net.bestia.zone.ai.goal.goals.WanderGoal
import net.bestia.zone.ai.planner.GoapActionRegistry
import net.bestia.zone.ai.planner.actions.ApproachTargetAction
import net.bestia.zone.ai.planner.actions.FleeToSafetyAction
import net.bestia.zone.ai.planner.actions.MeleeAttackAction
import net.bestia.zone.ai.planner.actions.WanderAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AiProfileRegistryTest {

  private fun newRegistry() = AiProfileRegistry(
    curveRegistry = CurveRegistry(listOf(IdentityCurve(), InverseCurve(), LinearRisingCurve())),
    inputRegistry = ConsiderationInputRegistry(listOf(EnemyInSightInput(), OwnHealthPctInput(), TraitInput())),
    goalRegistry = GoalRegistry(listOf(KillEnemyGoal(), FleeGoal(), WanderGoal())),
    actionRegistry = GoapActionRegistry(
      listOf(ApproachTargetAction(), MeleeAttackAction(), FleeToSafetyAction(), WanderAction())
    )
  )

  @Test
  fun `both shipped archetypes parse and resolve`() {
    val registry = newRegistry()

    registry.load()

    assertNotNull(registry.get("aggressive_melee"))
    assertNotNull(registry.get("passive_wanderer"))

    val aggressive = registry.getOrThrow("aggressive_melee")
    assertEquals("wild_beasts", aggressive.faction)
    assertEquals(8, aggressive.perception.sightRadius)
    assertEquals(0.8, aggressive.traits["aggression"])
  }

  @Test
  fun `an unknown action id fails fast`() {
    val registry = newRegistry()

    val dto = AiProfileDto(
      identifier = "broken",
      actions = listOf("does_not_exist")
    )

    assertThrows(IllegalArgumentException::class.java) { registry.register(dto) }
  }

  @Test
  fun `an unknown response curve fails fast`() {
    val registry = newRegistry()

    val dto = AiProfileDto(
      identifier = "broken",
      goals = listOf(
        AiProfileDto.GoalDto(
          name = "kill_enemy",
          considerations = listOf(
            AiProfileDto.ConsiderationDto(input = "enemy_in_sight", curve = "no_such_curve")
          )
        )
      )
    )

    assertThrows(IllegalArgumentException::class.java) { registry.register(dto) }
  }
}
