package net.bestia.zone.ai.profile

import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

/**
 * A bestia's orders: what it does while its owner is not driving it, and the only AI surface a player can change.
 * See `bestia-docs/content/docs/mechanics/bestias/bestia-orders.md`.
 *
 * ### Why a fixed vocabulary rather than the goal list
 *
 * The obvious design — show the player the archetype's goals with a priority slider each — leaks internal goal
 * names into the client, is impossible to balance, and gives no way to say "only while idling". Worse, priority
 * is an input to an A* search, so unconstrained values are unconstrained planner cost.
 *
 * A small enumerated vocabulary avoids all of that. Every [job] compiles to a fixed, reviewed set of goals
 * (see [BestiaJob.goalNames]), and every [courage] step to a fixed aggression. The player expresses intent; the
 * server decides what that means.
 *
 * Stored inline on `PlayerBestia`. All fields are defaulted, which is also what gives Kotlin the no-arg
 * constructor Hibernate needs for an embeddable.
 */
@Embeddable
data class AiConfig(
  @Enumerated(EnumType.STRING)
  val job: BestiaJob = BestiaJob.REST,

  @Enumerated(EnumType.STRING)
  val courage: Courage = Courage.STEADY,
) {

  companion object {
    /**
     * Goals every owned bestia gets on top of its job, whatever its species: looking after itself. They are the
     * one place orders widen the archetype, and they add no way to hurt anybody.
     */
    val SELF_CARE_GOALS = listOf("HealFromBag", "EatFromBag")

    /** The actions that carry [SELF_CARE_GOALS]. */
    val SELF_CARE_ACTIONS = listOf("healFromBag", "eatFromBag")
  }
}

/**
 * What a bestia works at while its owner is away.
 *
 * Each job names the goals it permits. The set is *intersected* with the archetype's own goals rather than
 * replacing them, so a job can never grant a creature a behaviour its species does not have — a critter told to
 * GUARD still cannot hunt, because its profile has no kill-on-sight goal to enable.
 *
 * `KillAttacker` is in every job on purpose: self-defence is not an order a player gets to switch off, and an
 * owned creature that stood still while being killed would be a bug reported as one.
 *
 * Gather, Craft, Mine and Follow join once the goals that carry them exist.
 */
enum class BestiaJob(val goalNames: Set<String>) {

  /** Roam the area around home and engage anything hostile that comes near. */
  GUARD(setOf("KillAttacker", "KillEnemy", "Wander", "ReturnHome")),

  /** Stay around home and look after itself: eat and sleep as needed. The default. */
  REST(setOf("KillAttacker", "Wander", "ReturnHome", "EatVegetation", "Sleep"));

  companion object {
    fun fromNameOrNull(name: String): BestiaJob? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
  }
}

/**
 * How a bestia meets danger, as one slider of five steps.
 *
 * For now a step only sets how strongly the bestia wants its kill goals. Retreat lines and shelter join with the
 * goals that carry them, read from the same step, so the slider the player sees does not change meaning.
 */
enum class Courage(val aggression: Int) {
  TIMID(10),
  CAREFUL(30),

  /** The archetype-neutral middle, and the default. */
  STEADY(50),
  BOLD(75),
  BRAVE(100);

  companion object {
    fun fromNameOrNull(name: String): Courage? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
  }
}
