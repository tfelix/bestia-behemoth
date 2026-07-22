package net.bestia.zone.ai.goap2.bestia.profile

import net.bestia.zone.ai.goap2.bestia.AttackDefinition

/**
 * Immutable, validated runtime representation of a bestia AI archetype — the goap2 counterpart to
 * [net.bestia.zone.ai.profile.AiProfile], parsed from `resources/ai/goap2/<name>.yml`. Many mobs can
 * share one profile; it only holds tuning knobs and *which* goals/actions apply, never per-entity
 * runtime state (that lives on the agent's [net.bestia.zone.ai.goap2.state.Blackboard]).
 */
data class BestiaAiProfile(
  val identifier: String,
  val faction: String?,
  val wanderRadius: Long,
  val hungerThreshold: Int,
  val tirednessThreshold: Int,
  val meleeRange: Long,
  val goals: List<GoalTuning>,
  val actionIds: List<String>,
  val attacks: List<AttackDefinition>,
) {

  /** Which of [net.bestia.zone.ai.goap2.bestia.BestiaDomain.Goals.ALL] this archetype pursues, with its own base priority. */
  data class GoalTuning(val name: String, val basePriority: Float)

  companion object {
    fun fromDto(dto: BestiaAiProfileDto): BestiaAiProfile = BestiaAiProfile(
      identifier = dto.identifier,
      faction = dto.faction,
      wanderRadius = dto.wanderRadius,
      hungerThreshold = dto.hungerThreshold,
      tirednessThreshold = dto.tirednessThreshold,
      meleeRange = dto.meleeRange,
      goals = dto.goals.map { GoalTuning(it.name, it.basePriority) },
      actionIds = dto.actions,
      attacks = dto.attacks.map { AttackDefinition(it.id, it.range, it.baseCost) },
    )
  }
}
