package net.bestia.zone.ai.profile

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import net.bestia.zone.ai.domain.bestia.ActivityCycle

/**
 * Jackson mirror of a `resources/ai/<name>.yml` archetype — the one AI profile format, replacing the two
 * that used to exist side by side under `ai/` and `ai/goap2/`.
 *
 * Every field is nullable or defaulted so adding a key never breaks an existing file, and snake_case in
 * YAML maps to camelCase here.
 *
 * ### What YAML can and cannot say
 *
 * It selects and it tunes: *which* goals this archetype pursues and at what base priority, *which* action
 * templates it may use, its attacks, and its numeric knobs. It cannot express behaviour. A goal's priority
 * formula — the considerations and response curves that scale it with hunger, health or aggression — lives
 * in Kotlin next to the goal, in the `priority { consider(...) }` DSL.
 *
 * That is a deliberate narrowing of the old format, which let YAML assemble considerations from
 * `input`/`curve`/`weight` triples resolved through two bean registries. Losing it costs a rebuild to
 * retune a curve and buys type safety, one less indirection to trace when a mob misbehaves, and a much
 * smaller surface to validate — which matters most for player-supplied configuration, where the only thing
 * a player can move is a base priority and every value has to be clamped.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AiProfileDto(
  val identifier: String,
  /** Pack/faction id; agents sharing one share a team blackboard. */
  val faction: String? = null,
  val perception: PerceptionDto = PerceptionDto(),
  /**
   * When this species sleeps. Defaults to [ActivityCycle.CATHEMERAL], which is what every archetype did
   * before day/night mattered — sleep when tired, whatever the hour — so adding the key changed nothing for
   * the profiles that do not set it.
   */
  val activityCycle: ActivityCycle = ActivityCycle.CATHEMERAL,
  val wanderRadius: Long = 5,
  val meleeRange: Long = 1,
  val hungerThreshold: Int = 85,
  val tirednessThreshold: Int = 80,
  val restlessThreshold: Int = 60,
  /** Health percentage at or below which this archetype would rather run than fight. */
  val fleeThresholdPct: Int = 35,
  /**
   * 0..100 temperament knob feeding the kill goals' priority curves.
   *
   * There is deliberately no separate `courage`: how readily a creature gives up the fight is already what
   * [fleeThresholdPct] says, and two knobs for one concept only invited them to disagree.
   */
  val aggression: Int = 50,
  val goals: List<GoalDto> = emptyList(),
  val actions: List<String> = emptyList(),
  val attacks: List<AttackDto> = emptyList(),
) {

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class PerceptionDto(
    val sightRadius: Int = 8,
    /**
     * How long after the last hit this archetype keeps hunting whoever landed it.
     *
     * This is the leash on a grudge, and it is per-archetype because it is the whole difference between a
     * creature that snaps back and one that chases you home. It used to be a constant in the perception
     * system; the default reproduces it exactly.
     */
    val aggroMemorySeconds: Float = 10f,
  )

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class GoalDto(val name: String, val basePriority: Float? = null)

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class AttackDto(
    val id: String,
    val range: Long,
    val baseCost: Float = 5f,

    /**
     * Left unset for a plain bite or swing, which is what most profiles want - see
     * [net.bestia.zone.ai.domain.bestia.AttackDefinition.skillId]. Naming a `skills.yml` id here makes the
     * creature *cast* instead, and it must then know that skill.
     */
    val skillId: Long? = null,

    val cooldownSeconds: Float = 1.5f,
  )
}
