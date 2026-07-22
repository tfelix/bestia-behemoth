package net.bestia.zone.ai.goap2.bestia.profile

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

/**
 * Jackson mirror of a `resources/ai/goap2/<name>.yml` archetype. Lives in its own `ai/goap2/`
 * classpath folder rather than the old system's `ai/` — [net.bestia.zone.ai.profile.AiProfileRegistry]
 * only globs the top-level `ai` folder (not subfolders), so the two profile formats can never
 * collide at boot.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BestiaAiProfileDto(
  val identifier: String,
  val faction: String? = null,
  val wanderRadius: Long = 5,
  val hungerThreshold: Int = 85,
  val tirednessThreshold: Int = 80,
  val meleeRange: Long = 1,
  val goals: List<GoalDto> = emptyList(),
  val actions: List<String> = emptyList(),
  val attacks: List<AttackDto> = emptyList(),
) {

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class GoalDto(val name: String, val basePriority: Float = 0f)

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class AttackDto(val id: String, val range: Long, val baseCost: Float = 5f)
}
