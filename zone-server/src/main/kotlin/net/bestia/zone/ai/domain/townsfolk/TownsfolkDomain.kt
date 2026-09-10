package net.bestia.zone.ai.domain.townsfolk

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.core.action.ActionResolver
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.action.CompositeActionResolver
import net.bestia.zone.ai.core.goal.Combine
import net.bestia.zone.ai.core.goal.Curve
import net.bestia.zone.ai.core.goal.Goal
import net.bestia.zone.ai.core.goal.linear
import net.bestia.zone.ai.core.goal.priority
import net.bestia.zone.ai.core.precondition.Precondition
import net.bestia.zone.ai.core.precondition.Preconditions
import net.bestia.zone.ai.core.state.CommonKeys
import net.bestia.zone.ai.core.state.Drive
import net.bestia.zone.ai.core.state.RestingWindow
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.AiDomainCatalogue
import net.bestia.zone.ai.domain.townsfolk.action.GoHomeActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.LoiterActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.SleepAtHomeActionTemplate

/**
 * The people who live in the towns the generator already builds.
 *
 * A second domain beside `BestiaDomain` rather than more goals inside it, because almost nothing carries
 * over. A townsperson has no territory, no aggression and no prey; what it has is a clock, an address and
 * eventually a trade. The two share only what every agent has - where it is, how tired it is - and those
 * keys live in [CommonKeys] for exactly that reason.
 *
 * ### What this branch can express
 *
 * A day with a bedtime in it, and no work yet: loiter about, go home when you have drifted, sleep at home
 * once it is late. Occupations and the shifts that go with them come next; the three goals here are the
 * skeleton those hang off, and the one that has to exist before any of it is [Goals.LOITER].
 */
object TownsfolkDomain : AiDomainCatalogue {

  /** What a profile's `domain:` names to get this one. See [net.bestia.zone.ai.domain.AiDomains]. */
  const val ID = "townsfolk"

  override val id = ID

  // ------------------------------------------------------------ shared with every domain

  val POSITION = CommonKeys.POSITION
  val HOME_POSITION = CommonKeys.HOME_POSITION
  val HOUR_OF_DAY = CommonKeys.HOUR_OF_DAY
  val TIREDNESS = CommonKeys.TIREDNESS
  val TIREDNESS_THRESHOLD = CommonKeys.TIREDNESS_THRESHOLD
  val RESTLESSNESS = CommonKeys.RESTLESSNESS
  val RESTED = CommonKeys.RESTED
  val WANDER_RADIUS = CommonKeys.WANDER_RADIUS

  // ------------------------------------------------------------------------------ hours

  /**
   * When a commoner turns in and gets up, on the world calendar's own 0..23 hour.
   *
   * A property of this archetype rather than of the world: full night runs 22:00 to 04:00, and somebody
   * who rose at four would be about two hours before there is any light. Occupations will each want their
   * own pair - a baker's day starts well before a shopkeeper's - which is why the goals read these through
   * [isBedtime] rather than testing the hour themselves.
   */
  const val BEDTIME_HOUR = 22
  const val RISE_HOUR = 6

  // ---------------------------------------------------------------------------- distances

  /** How far from the door a townsperson drifts while it has nothing else to do. In tiles. */
  const val DEFAULT_LOITER_RADIUS = 12L

  /**
   * Close enough to the door to count as home, in tiles.
   *
   * Sleeping is gated on it, so it is what makes the planner produce `goHome -> sleepAtHome` rather than
   * lying down in the street. There are no interiors yet, so "home" is the doorstep.
   */
  const val DOORSTEP_RADIUS = 1L

  // ------------------------------------------------------------------------------ drives

  /** Tiredness at or below which a townsperson counts as rested. Shared by the goal and the sleep leaf. */
  const val RESTED_TIREDNESS = 20

  /** Restlessness at or below which there is nothing left to walk off. See [Goals.LOITER]. */
  const val SETTLED_RESTLESSNESS = 20

  const val DEFAULT_TIREDNESS_THRESHOLD = 80

  /**
   * The same two appetites the wild creatures have, at the same rates.
   *
   * Hunger is deliberately absent until there is somewhere to buy food: a drive that climbs to a hundred
   * with no action able to spend it is a townsperson permanently pursuing a goal it can never satisfy.
   */
  val DRIVES = listOf(
    Drive(TIREDNESS, perGameHour = 300f, whileSleepingPerGameHour = -6_000f),
    Drive(RESTLESSNESS, perGameHour = 1_920f),
  )

  /** Perception clears [RESTED] through the night, which is what keeps [Goals.SLEEP] unsatisfied until dawn. */
  val RESTING_WINDOW = RestingWindow { hour, _ -> isBedtime(hour) }

  // ----------------------------------------------------------------------------- helpers

  fun isBedtime(hour: Int): Boolean {
    return hour >= BEDTIME_HOUR || hour < RISE_HOUR
  }

  /** False rather than null when the hour is unknown: an agent that has not perceived yet is not in bed. */
  fun isBedtime(state: WorldState): Boolean {
    val hour = state.get(HOUR_OF_DAY) ?: return false
    return isBedtime(hour)
  }

  fun isAtHome(state: WorldState): Boolean {
    val home = state.get(HOME_POSITION) ?: return false
    val position = state.get(POSITION) ?: return false
    return position.distance(home) <= DOORSTEP_RADIUS
  }

  private fun loiterRadiusOf(state: WorldState): Long {
    return state.get(WANDER_RADIUS) ?: DEFAULT_LOITER_RADIUS
  }

  /** Null, not a huge number, when either position is unknown - see `BestiaDomain.homeDistanceOrNull`. */
  private fun homeDistanceOrNull(state: WorldState): Long? {
    val home = state.get(HOME_POSITION) ?: return null
    val position = state.get(POSITION) ?: return null
    return position.distance(home)
  }

  private val bedtimeUrgency = Curve { state -> if (isBedtime(state)) 0.9 else 0.0 }

  object Goals {

    /**
     * Walking home, for its own sake rather than to sleep.
     *
     * Outranks loitering so a townsperson that has drifted comes back before ambling further, and is
     * outranked by sleeping so that at bedtime the *sleep* plan decides the journey - it walks home for a
     * reason and lies down on arrival, instead of arriving and then reconsidering.
     */
    val GO_HOME = Goal(
      name = "GoHome",
      priority = priority(base = 70f),
      availability = Precondition { s -> (homeDistanceOrNull(s) ?: 0L) > loiterRadiusOf(s) },
      desiredState = listOf(Precondition { s -> (homeDistanceOrNull(s) ?: Long.MAX_VALUE) <= loiterRadiusOf(s) }),
    )

    /**
     * The floor. Always available, so there is never a moment with no goal at all.
     *
     * That is not decoration: `Planner.selectCurrentGoal` returns null when nothing is both available and
     * unsatisfied, the think stage then clears the plan, and a townsperson with only errands to run would
     * stand rooted between them. Restlessness is what gives this something real to want - the same drive
     * that makes a creature wander - so "loitering" is a person walking a few paces and pausing, not a
     * lowest-priority goal that is permanently unsatisfiable.
     */
    val LOITER = Goal(
      name = "Loiter",
      priority = priority(base = 10f),
      availability = Precondition { true },
      desiredState = listOf(Preconditions.atMost(RESTLESSNESS, SETTLED_RESTLESSNESS)),
    )

    /**
     * Bed, because it is late or because the day was long.
     *
     * [Combine.MAX] for the reason `BestiaDomain.Goals.SLEEP` uses it: averaged, a wide-awake townsperson
     * at ten in the evening would score half of what the hour alone says, and drop below [GO_HOME].
     *
     * Both desired conditions are needed. A tiredness ceiling on its own is already met by somebody who
     * slept well, so at nightfall the goal would count as satisfied and be skipped - see [CommonKeys.RESTED].
     */
    val SLEEP = Goal(
      name = "Sleep",
      priority = priority(base = 90f, combine = Combine.MAX) {
        consider(TIREDNESS.linear())
        consider(bedtimeUrgency)
      },
      availability = Precondition { s ->
        isBedtime(s) || (s.get(TIREDNESS) ?: 0) >= (s.get(TIREDNESS_THRESHOLD) ?: DEFAULT_TIREDNESS_THRESHOLD)
      },
      desiredState = listOf(
        Preconditions.atMost(TIREDNESS, RESTED_TIREDNESS),
        Preconditions.equalTo(RESTED, true),
      ),
    )

    val ALL = listOf(
      GO_HOME,
      LOITER,
      SLEEP,
    )

    val BY_NAME = ALL.associateBy { it.name }
  }

  /** What a template needs beyond the planning contract. Only movement, so far. */
  data class Collaborators(val locomotion: Locomotion)

  private val TEMPLATE_FACTORIES: Map<String, (Collaborators) -> ActionTemplate> = mapOf(
    "goHome" to { c -> GoHomeActionTemplate(c.locomotion) },
    "loiter" to { c -> LoiterActionTemplate(c.locomotion) },
    "sleepAtHome" to { _ -> SleepAtHomeActionTemplate() },
  )

  override val actionIds: Set<String> get() = TEMPLATE_FACTORIES.keys

  override val goalsByName: Map<String, Goal> get() = Goals.BY_NAME

  fun resolver(actionIds: List<String>, collaborators: Collaborators): ActionResolver {
    val catalog = TEMPLATE_FACTORIES.mapValues { (_, build) -> build(collaborators) }
    return CompositeActionResolver(actionIds.mapNotNull { catalog[it] })
  }
}
