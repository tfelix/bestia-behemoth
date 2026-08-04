package net.bestia.zone.navigation.profile

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import net.bestia.worldgen.core.MovementMode

/**
 * What a species can do, and what it would rather not: the per-creature half of route planning.
 *
 * The graph says what is *possible* and this says what is *preferred*, which is the split that lets one
 * generated graph serve every creature in the game. A merchant discounts roads and a wild animal marks them
 * up, and neither needs its own copy of the world's routes.
 */
class MovementProfile(
  val identifier: String,
  /** Capabilities this creature has. An edge demanding anything outside this set is unusable. */
  val capabilities: Set<MovementMode>,
  /** Half the creature's width, in metres. Compared against a crossing's own limit. */
  val agentHalfWidth: Double,
  /** Multiplier on a made surface. Below one prefers roads, above one avoids them. */
  val roadCostMultiplier: Double,
  /** Multiplier on open country. */
  val offRoadCostMultiplier: Double
) {

  init {
    require(identifier.isNotBlank()) { "A movement profile needs an identifier" }
    require(MovementMode.WALK in capabilities) {
      "Profile '$identifier' cannot walk, which no creature the macro graph serves can afford - " +
          "every land edge in the world demands WALK, so this profile could never travel at all"
    }
    require(agentHalfWidth > 0.0) { "Profile '$identifier' has no width, was $agentHalfWidth" }
    require(roadCostMultiplier > 0.0) {
      "Profile '$identifier' has a non-positive road multiplier $roadCostMultiplier; zero or less would " +
          "make a road free or paid-for-travelling and break the search"
    }
    require(offRoadCostMultiplier > 0.0) {
      "Profile '$identifier' has a non-positive off-road multiplier $offRoadCostMultiplier"
    }
  }

  /**
   * Whether this creature can use an edge at all.
   *
   * [modes] is a conjunction - see `worldgen`'s `MovementMode`. A stream crossing carries `{WALK, SWIM}` and
   * a creature that only walks must be refused it, which is why this is `containsAll` and not `any`.
   */
  fun canTraverse(modes: Set<MovementMode>, maxAgentHalfWidth: Double): Boolean =
    capabilities.containsAll(modes) && agentHalfWidth <= maxAgentHalfWidth

  fun costMultiplier(isMadeSurface: Boolean): Double =
    if (isMadeSurface) roadCostMultiplier else offRoadCostMultiplier

  override fun toString() = "MovementProfile[$identifier]"

  companion object {

    /**
     * What a creature with nothing configured gets.
     *
     * Present so that adding navigation does not require authoring a profile for every species that already
     * exists - an unconfigured bestia walks, does not swim, is small, and has no opinion about roads.
     */
    const val DEFAULT_IDENTIFIER = "default_ground_walker"

    fun fromDto(dto: MovementProfileDto): MovementProfile {
      val capabilities = mutableSetOf(MovementMode.WALK)
      if (dto.canSwim) capabilities.add(MovementMode.SWIM)
      if (dto.canClimb) capabilities.add(MovementMode.CLIMB)

      return MovementProfile(
        identifier = dto.identifier,
        capabilities = capabilities,
        agentHalfWidth = dto.agentHalfWidth,
        roadCostMultiplier = dto.roadCostMultiplier,
        offRoadCostMultiplier = dto.offRoadCostMultiplier
      )
    }
  }
}

/**
 * The YAML shape of a [MovementProfile].
 *
 * Capabilities are spelled as `can_*` booleans rather than a list of mode names, because that is what a
 * person writing a species file is actually deciding - and it keeps the generator's enum out of content
 * files, so renaming a mode is not a rename across every YAML in the tree.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class MovementProfileDto(
  val identifier: String = "",
  val canSwim: Boolean = false,
  /** Whether it can cross ground steep enough to have been tagged `CLIMB` by the generator. */
  val canClimb: Boolean = true,
  val agentHalfWidth: Double = 0.5,
  val roadCostMultiplier: Double = 1.0,
  val offRoadCostMultiplier: Double = 1.0
)
