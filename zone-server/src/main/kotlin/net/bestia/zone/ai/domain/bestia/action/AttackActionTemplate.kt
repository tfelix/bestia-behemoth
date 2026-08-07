package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.ai.bt.selector
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.bt.leaves.UseSkill
import net.bestia.zone.ai.domain.bestia.AttackDefinition
import net.bestia.zone.ai.domain.bestia.AttackEffectiveness
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.EffectivenessKey
import net.bestia.zone.battle.skill.SkillExecutionService

/**
 * Grounds one attack action per known [attacks] entry currently in range. All of them lead to the same
 * [BestiaDomain.TARGET_DEAD] outcome, so which one the planner picks comes down purely to
 * [Action.cost] — and that cost is scaled by [BestiaDomain.ATTACK_EFFECTIVENESS] (cheaper the more
 * effective it's remembered to be, a neutral [AttackEffectiveness.UNKNOWN_ESTIMATE] for anything never
 * tried against this archetype). This is what "pick the most suitable attack, try attacks out, remember
 * what works well" reduces to: no separate decision mechanism needed, A* already picks the cheapest path.
 */
class AttackActionTemplate(
  private val attacks: List<AttackDefinition>,
  private val skills: SkillExecutionService,
) : ActionTemplate {
  override val id = "attack"

  override fun ground(state: WorldState): List<Action> {
    val position = state.get(BestiaDomain.POSITION) ?: return emptyList()
    val targetPosition = state.get(BestiaDomain.TARGET_POSITION) ?: return emptyList()
    val targetId = state.get(BestiaDomain.TARGET_ID) ?: return emptyList()
    val archetype = state.get(BestiaDomain.TARGET_ARCHETYPE) ?: UNKNOWN_ARCHETYPE
    val distance = position.distance(targetPosition)
    val effectiveness = state.get(BestiaDomain.ATTACK_EFFECTIVENESS) ?: emptyMap()

    return attacks
      .filter { distance <= it.range }
      .map { attack ->
        val estimate = effectiveness[EffectivenessKey(archetype, attack.id)] ?: AttackEffectiveness.UNKNOWN_ESTIMATE
        Action(
          name = "attack(${attack.id})",
          effects = listOf(Effects.set(BestiaDomain.TARGET_DEAD, true)),
          cost = { attack.baseCost * (1.5f - estimate.toFloat()) },
          behavior = { fightUntilDead(targetId, attack) },
        )
      }
  }

  /**
   * Keeps striking until the target is actually dead, and only *then* reports SUCCESS.
   *
   * The distinction matters because the action's effect claims `TARGET_DEAD`, and effects are written
   * back on success — so an action that succeeded after a single swing would leave the bestia believing
   * it had killed something that is still hitting it. Succeeding only on a real death keeps the belief
   * honest, and the whole fight is one plan step rather than a replan per swing.
   *
   * The selector reads in priority order: if the target is gone we are done; otherwise swing when off
   * cooldown (`optional`, because being mid-cooldown is not a failure) and report RUNNING.
   */
  private fun fightUntilDead(targetId: Long, attack: AttackDefinition) = selector {
    condition("target is dead") { ctx -> !ctx.world.isAlive(targetId) }
    sequence {
      optional { cooldown(attack.cooldownSeconds) { node(UseSkill(targetId, attack.skillId, skills)) } }
      run("still fighting") { Status.RUNNING }
    }
  }

  companion object {
    private const val UNKNOWN_ARCHETYPE = "unknown"
  }
}
