package net.bestia.zone.ai.profile

import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

/**
 * What a bestia does while its owner is not driving it, and the only AI surface a player can change.
 *
 * ### Why a fixed vocabulary rather than the goal list
 *
 * The obvious design — show the player the archetype's goals with a priority slider each — leaks internal goal
 * names into the client, is impossible to balance, and gives no way to say "only while idling". Worse, priority
 * is an input to an A* search, so unconstrained values are unconstrained planner cost.
 *
 * A small enumerated vocabulary avoids all of that. Every [stance] compiles to a fixed, reviewed set of goals
 * (see [IdleStance.goalNames]), and the two numeric knobs are clamped on the way in. The player expresses
 * intent; the server decides what that means.
 *
 * Stored inline on `PlayerBestia`. All fields are defaulted, which is also what gives Kotlin the no-arg
 * constructor Hibernate needs for an embeddable.
 */
@Embeddable
data class AiConfig(
  @Enumerated(EnumType.STRING)
  val stance: IdleStance = IdleStance.PATROL,

  /** 0..100; how strongly the creature wants the kill goals it has. */
  val aggression: Int = DEFAULT_AGGRESSION,

  /** 0..100; health percentage at or below which it breaks off and runs. */
  val fleeThresholdPct: Int = DEFAULT_FLEE_THRESHOLD_PCT,
) {

  /**
   * The same config with both numbers forced into range.
   *
   * Clamping rather than rejecting is deliberate for values that arrive over the wire: a client sending 5000
   * aggression is asking for "as aggressive as possible", and answering that with an error achieves nothing a
   * clamp does not. Anything structural — an unknown stance — cannot be clamped and is refused at the handler
   * instead.
   */
  fun sanitised(): AiConfig = copy(
    aggression = aggression.coerceIn(0, 100),
    fleeThresholdPct = fleeThresholdPct.coerceIn(0, 100),
  )

  companion object {
    const val DEFAULT_AGGRESSION = 50
    const val DEFAULT_FLEE_THRESHOLD_PCT = 35
  }
}

/**
 * A player-chosen standing order for an idle bestia.
 *
 * Each stance names the goals it permits. The set is *intersected* with the archetype's own goals rather than
 * replacing them, so a stance can never grant a creature a behaviour its species does not have — a critter told
 * to DEFEND still cannot hunt, because its profile has no kill-on-sight goal to enable.
 *
 * `KillAttacker` and `Flee` are in every stance on purpose: self-defence is not a standing order a player gets
 * to switch off, and an owned creature that stood still while being killed because it was told to HOLD would be
 * a bug reported as one.
 */
enum class IdleStance(val goalNames: Set<String>) {

  /** Stay put. Defends itself, but will not wander off, forage or pick fights. */
  HOLD(setOf("KillAttacker", "Flee")),

  /** Roam around where it was left, and come back if it strays. The default. */
  PATROL(setOf("KillAttacker", "Flee", "Wander", "ReturnHome")),

  /** Look after itself: eat and sleep as needed, roam meanwhile. */
  FORAGE(setOf("KillAttacker", "Flee", "Wander", "ReturnHome", "EatVegetation", "Sleep")),

  /** Engage anything hostile that comes near, rather than waiting to be hit first. */
  DEFEND(setOf("KillAttacker", "Flee", "KillEnemy", "ReturnHome"));

  companion object {
    fun fromNameOrNull(name: String): IdleStance? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
  }
}
