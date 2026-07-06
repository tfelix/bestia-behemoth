package net.bestia.zone.ai.profile

/**
 * Immutable, validated runtime representation of an AI archetype (parsed from a
 * `resources/ai/<name>.yml` file). Many mobs can share one profile; it holds only static behaviour
 * configuration, never any
 * per-entity runtime state (that lives on the `Brain` component).
 */
data class AiProfile(
  val identifier: String,
  val faction: String?,
  val traits: Map<String, Double>,
  val perception: Perception,
  val goals: List<GoalSpec>,
  val actionIds: List<String>
) {

  data class Perception(
    val sightRadius: Int,
    val sightInterval: Double,
    val reactsTo: Set<String>
  )

  /**
   * The utility-scoring configuration for a single goal, bound to a `Goal` bean by [name].
   */
  data class GoalSpec(
    val name: String,
    val baseScore: Double,
    val combine: AiProfileDto.Combine,
    val considerations: List<Consideration>
  )

  data class Consideration(
    val input: String,
    val curve: String,
    val weight: Double
  )

  companion object {
    fun fromDto(dto: AiProfileDto): AiProfile {
      return AiProfile(
        identifier = dto.identifier,
        faction = dto.faction,
        traits = dto.traits,
        perception = Perception(
          sightRadius = dto.perception.sightRadius,
          sightInterval = dto.perception.sightInterval,
          reactsTo = dto.perception.reactsTo.toSet()
        ),
        goals = dto.goals.map { goal ->
          GoalSpec(
            name = goal.name,
            baseScore = goal.baseScore,
            combine = goal.combine,
            considerations = goal.considerations.map { consideration ->
              Consideration(
                input = consideration.input,
                curve = consideration.curve,
                weight = consideration.weight
              )
            }
          )
        },
        actionIds = dto.actions
      )
    }
  }
}
