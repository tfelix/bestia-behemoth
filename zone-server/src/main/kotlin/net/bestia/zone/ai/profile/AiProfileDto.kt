package net.bestia.zone.ai.profile

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

/**
 * Jackson DTOs mirroring the `resources/ai/<name>.yml` archetype files. Every field is nullable or
 * defaulted so that adding new keys never breaks an existing archetype file (forward-compatible),
 * mirroring the tolerant parsing used by the mob/item importers.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AiProfileDto(
  val identifier: String,
  val faction: String? = null,
  val traits: Map<String, Double> = emptyMap(),
  val perception: PerceptionDto = PerceptionDto(),
  val goals: List<GoalDto> = emptyList(),
  val actions: List<String> = emptyList()
) {

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class PerceptionDto(
    val sightRadius: Int = 8,
    val sightInterval: Double = 0.5,
    val reactsTo: List<String> = emptyList()
  )

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class GoalDto(
    val name: String,
    val baseScore: Double = 0.0,
    val combine: Combine = Combine.PRODUCT,
    val considerations: List<ConsiderationDto> = emptyList()
  )

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class ConsiderationDto(
    val input: String,
    val curve: String = "identity",
    val weight: Double = 1.0
  )

  enum class Combine {
    PRODUCT,
    MIN,
    MAX,
    AVERAGE
  }
}
