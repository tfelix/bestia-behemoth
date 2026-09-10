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
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.CommonKeys
import net.bestia.zone.ai.core.state.Drive
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ai.core.state.RestingWindow
import net.bestia.zone.ai.core.state.StateKey
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ecs.spawn.townsfolk.IndoorRegistry
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.ai.domain.AiDomainCatalogue
import net.bestia.zone.ai.domain.townsfolk.action.EnterHomeActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.GoHomeActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.GoToWorkActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.LoiterActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.SleepAtHomeActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.WorkShiftActionTemplate

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
  val DAY_INDEX = CommonKeys.DAY_INDEX

  // ------------------------------------------------------------------------ this domain

  /**
   * What this person does with a day, if anything.
   *
   * A fact about the individual rather than the archetype: a village's guard and its baker are the same
   * species running the same profile, and what separates them is written here when they are spawned.
   */
  val OCCUPATION = StateKey<Occupation>("occupation", retain = Blackboard.PERMANENT)

  /** Where the post is. Absent for somebody with no workplace, which makes [Goals.WORK_SHIFT] unavailable. */
  val WORK_POSITION = StateKey<Vec3L>("workPosition", retain = Blackboard.PERMANENT)

  /**
   * The prop id of the house, for somebody who has a real one.
   *
   * Absent for a person a GM put down at a bare coordinate, and that absence decides how they spend the
   * night: there is no door to go through, so they lie down on the spot instead. See
   * [net.bestia.zone.ai.domain.townsfolk.action.EnterHomeActionTemplate].
   */
  val HOME_BUILDING = StateKey<Long>("homeBuilding", retain = Blackboard.PERMANENT)

  /** Claimed by going through a door. Nothing reads it back - the agent is gone by then; see [Goals.SLEEP]. */
  val INDOORS = StateKey<Boolean>("indoors", retain = Blackboard.PERMANENT)

  /**
   * Where the nearest fight is, for as long as there is one.
   *
   * Written and cleared by [net.bestia.zone.ai.perception.ShelterSense] on every sweep, so its own TTL
   * never decides anything - the fight ending is what ends it, and that is also what sends people back
   * out into the street.
   */
  val THREAT_POSITION = StateKey<Vec3L>("threatPosition", observed = true)

  /** The doorway picked to run for, held until the fight is over so nobody dithers mid-street. */
  val SHELTER_DOOR = StateKey<Vec3L>("shelterDoor", observed = true)

  /**
   * Standing in a doorway out of the way of a fight.
   *
   * A *planning* device, exactly as [WORKED_ON_DAY] is, and never actually written: a shelter goal needs
   * a desired state some action can reach, and "the fight is over" is not one - nothing a townsperson
   * does ends it. Sheltering is a behaviour with no end of its own, so the action never reports success
   * and the effect stays a prediction. The goal losing its threat is what releases them.
   */
  val SHELTERED = StateKey<Boolean>("sheltered", retain = Blackboard.PERMANENT)

  /**
   * The [DAY_INDEX] on which the shift was last seen through.
   *
   * Mostly a *planning* device. [Goals.WORK_SHIFT] needs a desired state some action can actually reach,
   * or A* finds no plan and the townsperson stands still through their whole shift; "the hour is no longer
   * my shift" is not reachable, because nothing an agent does moves the clock.
   *
   * It is therefore normal for it to be absent even from somebody who worked all day, exactly as
   * [CommonKeys.RESTED] is absent from a creature that slept the night: the hour leaving the shift makes
   * the goal unavailable, and whether the behaviour got to report success first is a race with the think
   * stagger. Nothing reads it expecting a diary - what stops a second shift is the shift's own hours.
   *
   * A stamp rather than a flag so that when it *is* written nothing has to clear it: the day moves on and
   * the comparison stops matching. See [CommonKeys.DAY_INDEX], which exists for this.
   */
  val WORKED_ON_DAY = StateKey<Long>("workedOnDay", retain = Blackboard.PERMANENT)

  // ------------------------------------------------------------------------------ hours

  /**
   * When a commoner turns in and gets up, on the world calendar's own 0..23 hour.
   *
   * The default, for somebody with no occupation or one that keeps ordinary hours. Not a property of the
   * world: full night runs 22:00 to 04:00, and somebody who rose at four would be up about two hours
   * before there is any light. An occupation may declare its own pair - an innkeeper's night is shorter
   * and later - which is why every reader goes through [restHoursOf] rather than testing the hour itself.
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

  /**
   * Perception clears [RESTED] through these hours, which is what keeps [Goals.SLEEP] unsatisfied until
   * morning. Per person rather than per archetype, because a night watch that had the commoner's window
   * would be sent to bed in the middle of its own shift.
   */
  fun restingWindowFor(occupation: Occupation?): RestingWindow {
    val rest = restHoursOf(occupation)
    return RestingWindow { hour, _ -> rest.covers(hour) }
  }

  // ----------------------------------------------------------------------------- helpers

  /** When somebody with no reason to keep other hours is in bed. */
  val DEFAULT_REST = HourWindow(BEDTIME_HOUR, RISE_HOUR)

  fun restHoursOf(occupation: Occupation?): HourWindow {
    return occupation?.rest ?: DEFAULT_REST
  }

  /** False rather than null when the hour is unknown: an agent that has not perceived yet is not in bed. */
  fun isBedtime(state: WorldState): Boolean {
    val hour = state.get(HOUR_OF_DAY) ?: return false
    return restHoursOf(state.get(OCCUPATION)).covers(hour)
  }

  /** Whether the clock is inside this person's shift. False for anybody who has none. */
  fun isOnShift(state: WorldState): Boolean {
    val shift = state.get(OCCUPATION)?.shift ?: return false
    val hour = state.get(HOUR_OF_DAY) ?: return false
    return shift.covers(hour)
  }

  /**
   * Whether today's shift has already been seen through.
   *
   * True when the day is unknown, which is the safe direction: an agent that has not perceived yet must
   * not decide it is late for work.
   */
  fun hasWorkedToday(state: WorldState): Boolean {
    val today = state.get(DAY_INDEX) ?: return true
    return state.get(WORKED_ON_DAY) == today
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
     * The post, for as long as the shift lasts.
     *
     * Outranks [GO_HOME] on purpose: a post further from home than the loiter radius would otherwise have
     * the townsperson turned round and walked back the moment it arrived.
     *
     * Availability needs a workplace as well as an hour, and that is not defensive. `AiThinkSystem` plans
     * for the highest-priority available goal *only* - a goal it cannot find a plan for leaves the agent
     * with no plan at all rather than falling through - so an occupation with nowhere to work would freeze
     * somebody on the spot for the length of their shift.
     *
     * Satisfied by [WORKED_ON_DAY] carrying today, which is there to give the search something reachable
     * to aim at - see that key. A shift running past midnight would defeat the comparison, and none does;
     * when one has to, that is what changes.
     */
    val WORK_SHIFT = Goal(
      name = "WorkShift",
      priority = priority(base = 75f),
      availability = Precondition { s ->
        s.get(WORK_POSITION) != null && isOnShift(s) && !hasWorkedToday(s)
      },
      desiredState = listOf(Precondition { s -> hasWorkedToday(s) }),
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
     *
     * Two actions claim them, and exactly one is ever available: somebody with a real house goes through
     * its door, and somebody a GM put down at a bare coordinate lies down where they stand. The two are
     * kept apart by what they ground on rather than by cost, so the planner is never choosing between
     * them.
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
      WORK_SHIFT,
    )

    val BY_NAME = ALL.associateBy { it.name }
  }

  /** What a template needs beyond the planning contract. */
  data class Collaborators(val locomotion: Locomotion, val indoors: IndoorRegistry)

  private val TEMPLATE_FACTORIES: Map<String, (Collaborators) -> ActionTemplate> = mapOf(
    "goHome" to { c -> GoHomeActionTemplate(c.locomotion) },
    "goToWork" to { c -> GoToWorkActionTemplate(c.locomotion) },
    "loiter" to { c -> LoiterActionTemplate(c.locomotion) },
    "enterHome" to { c -> EnterHomeActionTemplate(c.indoors) },
    "sleepAtHome" to { _ -> SleepAtHomeActionTemplate() },
    "workShift" to { _ -> WorkShiftActionTemplate() },
  )

  override val actionIds: Set<String> get() = TEMPLATE_FACTORIES.keys

  override val goalsByName: Map<String, Goal> get() = Goals.BY_NAME

  fun resolver(actionIds: List<String>, collaborators: Collaborators): ActionResolver {
    val catalog = TEMPLATE_FACTORIES.mapValues { (_, build) -> build(collaborators) }
    return CompositeActionResolver(actionIds.mapNotNull { catalog[it] })
  }
}
