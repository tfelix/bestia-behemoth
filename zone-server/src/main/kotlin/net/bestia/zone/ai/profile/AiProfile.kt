package net.bestia.zone.ai.profile

import net.bestia.zone.ai.domain.bestia.AttackDefinition

/**
 * Immutable, validated runtime representation of an AI archetype, parsed from a `resources/ai/<name>.yml`
 * file. Many mobs can share one profile; it holds only static behaviour configuration, never any per-entity
 * runtime state (that lives on the `AiAgent` component and its blackboard).
 */
data class AiProfile(
  val identifier: String,
  val faction: String?,
  val perception: Perception,
  val tuning: Tuning,
  val goals: List<GoalTuning>,
  val actionIds: List<String>,
  val attacks: List<AttackDefinition>,
) {

  data class Perception(val sightRadius: Int)

  /**
   * The numeric knobs, written into an agent's memory as permanent facts when the profile is attached, so
   * goal availability and priority read them exactly like any other state.
   */
  data class Tuning(
    val wanderRadius: Long,
    val meleeRange: Long,
    val hungerThreshold: Int,
    val tirednessThreshold: Int,
    val restlessThreshold: Int,
    val fleeThresholdPct: Int,
    val aggression: Int,
  )

  /**
   * Which goal this archetype pursues, optionally overriding the base priority the goal declares in Kotlin.
   * A null [basePriority] means "use the goal's own".
   */
  data class GoalTuning(val name: String, val basePriority: Float?)

  companion object {
    fun fromDto(dto: AiProfileDto): AiProfile = AiProfile(
      identifier = dto.identifier,
      faction = dto.faction,
      perception = Perception(sightRadius = dto.perception.sightRadius),
      tuning = Tuning(
        wanderRadius = dto.wanderRadius,
        meleeRange = dto.meleeRange,
        hungerThreshold = dto.hungerThreshold,
        tirednessThreshold = dto.tirednessThreshold,
        restlessThreshold = dto.restlessThreshold,
        fleeThresholdPct = dto.fleeThresholdPct,
        aggression = dto.aggression,
      ),
      goals = dto.goals.map { GoalTuning(it.name, it.basePriority) },
      actionIds = dto.actions,
      attacks = dto.attacks.map {
        AttackDefinition(
          id = it.id,
          range = it.range,
          baseCost = it.baseCost,
          skillId = it.skillId,
          cooldownSeconds = it.cooldownSeconds,
        )
      },
    )
  }
}
