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
import net.bestia.zone.ai.perception.SettlementWork
import net.bestia.zone.ecs.spawn.townsfolk.IndoorRegistry
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.ai.domain.AiDomainCatalogue
import net.bestia.zone.ai.domain.townsfolk.action.BuyFoodActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.CollectStockActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.DeliverStockActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.GoToGatheringActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.GoToSupplierActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.MingleActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.EatActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.EnterHomeActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.GoHomeActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.GoToMealActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.GoToShelterActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.GoToWorkActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.LoiterActionTemplate
import net.bestia.zone.ai.domain.townsfolk.action.ShelterAtDoorActionTemplate
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
  val MINUTE_OF_DAY = CommonKeys.MINUTE_OF_DAY
  val DAY_OFFSET_MINUTES = CommonKeys.DAY_OFFSET_MINUTES
  val HUNGER = CommonKeys.HUNGER
  val HUNGER_THRESHOLD = CommonKeys.HUNGER_THRESHOLD
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

  /** Where a meal can be had, from [net.bestia.zone.ai.perception.SettlementSense]. */
  val MEAL_POSITION = StateKey<Vec3L>("mealPosition", observed = true, retain = Blackboard.PERMANENT)

  /** Whether the town has anything to sell. False is how a burnt field reaches the people in the square. */
  val MEAL_IN_STOCK = StateKey<Boolean>("mealInStock", observed = true, retain = Blackboard.PERMANENT)

  /**
   * Whether the town can supply what this person's trade consumes.
   *
   * Absent for everybody who keeps no shop, which is most people - a guard's shift needs no inputs, and
   * gating on a belief nobody writes would send the whole town home. Only ever false, never missing, for
   * a trade the economy does model.
   */
  val WORK_SUPPLIED = StateKey<Boolean>("workSupplied", observed = true, retain = Blackboard.PERMANENT)

  /** Where a shopkeeper fetches their stock, from [net.bestia.zone.ai.perception.SettlementSense]. */
  val SUPPLIER_POSITION = StateKey<Vec3L>("supplierPosition", observed = true, retain = Blackboard.PERMANENT)

  /** Whether the maker has any to hand over. False is the errand a burnt field cancels. */
  val SUPPLY_IN_STOCK = StateKey<Boolean>("supplyInStock", observed = true, retain = Blackboard.PERMANENT)

  /** Where off-duty townsfolk gather, from [net.bestia.zone.ai.perception.SettlementSense]. */
  val SOCIAL_POSITION = StateKey<Vec3L>("socialPosition", observed = true, retain = Blackboard.PERMANENT)

  /**
   * Carrying goods, collected and not yet put on the shelf.
   *
   * A real latch, [HAS_FOOD]'s twin and for [HAS_FOOD]'s reason: collecting and delivering are two actions
   * with a walk across town between them.
   */
  val CARRYING_STOCK = StateKey<Boolean>("carryingStock", retain = Blackboard.PERMANENT)

  /** The [DAY_INDEX] the shop was last restocked on. A planning device, exactly as [WORKED_ON_DAY] is. */
  val RESTOCKED_ON_DAY = StateKey<Long>("restockedOnDay", retain = Blackboard.PERMANENT)

  /** The [DAY_INDEX] the evening was spent out on. [WORKED_ON_DAY]'s twin, and for the same reason. */
  val SOCIALISED_ON_DAY = StateKey<Long>("socialisedOnDay", retain = Blackboard.PERMANENT)

  /**
   * Carrying a meal, bought and not yet eaten.
   *
   * A real latch rather than a planning device, unlike [WORKED_ON_DAY]: buying and eating are two
   * actions with a walk possible between them, so something has to remember that the first happened.
   */
  val HAS_FOOD = StateKey<Boolean>("hasFood", retain = Blackboard.PERMANENT)

  /**
   * Standing in a doorway out of the way of a fight.
   *
   * A *planning* device, exactly as [WORKED_ON_DAY] is, and never actually written: [Goals.TAKE_SHELTER]
   * needs a desired state some action can reach, and "the fight is over" is not one - nothing a
   * townsperson does ends it. Sheltering is a behaviour with no end of its own, so the action never
   * reports success and the effect stays a prediction. The goal losing its threat is what releases them.
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
  const val DEFAULT_HUNGER_THRESHOLD = 70

  /** Hunger at or below which a townsperson has eaten enough. */
  const val FED_HUNGER = 10

  /**
   * Appetites on a *person's* timescale rather than a creature's, which is the whole reason rates are
   * authored per game-hour: a wild animal is peckish in three real minutes, and a villager eats at noon.
   *
   * Tiredness fills a waking day and is cleared by a night, which is what it has to be for a bedtime to
   * mean anything - at the creatures' rate a townsperson was ready for bed every five real minutes and
   * only the shortness of the tests hid it. Hunger runs about three times a day.
   *
   * Restlessness is against the wall rather than the world clock, because it is the floor goal's fuel and
   * decides how long somebody stands in a street doing nothing: at 6 a second it passes
   * [SETTLED_RESTLESSNESS] about four seconds after loitering settled it.
   */
  val DRIVES = listOf(
    Drive.perGameHour(HUNGER, 10f),
    Drive.perGameHour(TIREDNESS, 5f, whileSleeping = -14f),
    Drive.perRealSecond(RESTLESSNESS, 6f),
  )

  /**
   * Perception clears [RESTED] through these hours, which is what keeps [Goals.SLEEP] unsatisfied until
   * morning. Per person rather than per archetype, because a night watch that had the commoner's window
   * would be sent to bed in the middle of its own shift.
   */
  fun restingWindowFor(occupation: Occupation?, offsetMinutes: Int): RestingWindow {
    val rest = restHoursOf(occupation)
    return RestingWindow { minuteOfDay, _ -> rest.coversMinute(minuteOfDay, offsetMinutes) }
  }

  // ----------------------------------------------------------------------------- helpers

  /** When somebody with no reason to keep other hours is in bed. */
  val DEFAULT_REST = HourWindow(BEDTIME_HOUR, RISE_HOUR)

  fun restHoursOf(occupation: Occupation?): HourWindow {
    return occupation?.rest ?: DEFAULT_REST
  }

  /** Somewhere to eat, and something to eat there - or a meal already in hand. */
  fun canEat(state: WorldState): Boolean {
    if (state.get(HAS_FOOD) == true) return true

    return state.get(MEAL_POSITION) != null && state.get(MEAL_IN_STOCK) == true
  }

  /** How far this person's own day sits off the stated hours. Zero for anybody who keeps them. */
  fun dayOffsetOf(state: WorldState): Int {
    return state.get(DAY_OFFSET_MINUTES) ?: 0
  }

  /** False rather than null when the clock is unknown: an agent that has not perceived yet is not in bed. */
  fun isBedtime(state: WorldState): Boolean {
    val minuteOfDay = state.get(MINUTE_OF_DAY) ?: return false
    return restHoursOf(state.get(OCCUPATION)).coversMinute(minuteOfDay, dayOffsetOf(state))
  }

  /** Whether the clock is inside this person's shift. False for anybody who has none. */
  fun isOnShift(state: WorldState): Boolean {
    val shift = state.get(OCCUPATION)?.shift ?: return false
    val minuteOfDay = state.get(MINUTE_OF_DAY) ?: return false
    return shift.coversMinute(minuteOfDay, dayOffsetOf(state))
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

  /**
   * Whether the day's work is possible at all.
   *
   * Absence means yes. Almost nobody has a recipe, so a missing belief has to read as "nothing is
   * stopping me" - the alternative empties every post in the world until a sense gets round to it.
   */
  fun hasSomethingToWorkWith(state: WorldState): Boolean {
    return state.get(WORK_SUPPLIED) != false
  }

  /** Whether this person's job is to stay put while there is fighting. False for anybody with no trade. */
  fun holdsGround(state: WorldState): Boolean {
    return state.get(OCCUPATION)?.holdsGround == true
  }

  fun hasRestockedToday(state: WorldState): Boolean {
    val today = state.get(DAY_INDEX) ?: return true
    return state.get(RESTOCKED_ON_DAY) == today
  }

  /** An errand with somewhere to go and something to collect when you get there. */
  fun canRestock(state: WorldState): Boolean {
    return state.get(SUPPLIER_POSITION) != null && state.get(SUPPLY_IN_STOCK) == true
  }

  fun isAtSupplier(state: WorldState): Boolean {
    val supplier = state.get(SUPPLIER_POSITION) ?: return false
    val position = state.get(POSITION) ?: return false
    return position.distance(supplier) <= DOORSTEP_RADIUS
  }

  fun isAtWork(state: WorldState): Boolean {
    val post = state.get(WORK_POSITION) ?: return false
    val position = state.get(POSITION) ?: return false
    return position.distance(post) <= DOORSTEP_RADIUS
  }

  fun isAtGathering(state: WorldState): Boolean {
    val spot = state.get(SOCIAL_POSITION) ?: return false
    val position = state.get(POSITION) ?: return false
    return position.distance(spot) <= DOORSTEP_RADIUS
  }

  fun hasSocialisedToday(state: WorldState): Boolean {
    val today = state.get(DAY_INDEX) ?: return true
    return state.get(SOCIALISED_ON_DAY) == today
  }

  /**
   * The hours between the end of the shift and bed.
   *
   * Null for somebody with no shift at all - a child or a beggar has no evening because they have had no
   * day, and giving them one would park them at the inn door from dawn onwards.
   */
  fun eveningOf(occupation: Occupation?): HourWindow? {
    val shift = occupation?.shift ?: return null
    return HourWindow(shift.toHour, restHoursOf(occupation).fromHour)
  }

  fun isEvening(state: WorldState): Boolean {
    val minuteOfDay = state.get(MINUTE_OF_DAY) ?: return false
    return eveningOf(state.get(OCCUPATION))?.coversMinute(minuteOfDay, dayOffsetOf(state)) == true
  }

  fun isAtMeal(state: WorldState): Boolean {
    val stall = state.get(MEAL_POSITION) ?: return false
    val position = state.get(POSITION) ?: return false
    return position.distance(stall) <= DOORSTEP_RADIUS
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
        s.get(WORK_POSITION) != null && isOnShift(s) && !hasWorkedToday(s) && hasSomethingToWorkWith(s)
      },
      desiredState = listOf(Precondition { s -> hasWorkedToday(s) }),
    )

    /**
     * A doorway, because there is fighting in the street.
     *
     * Above everything including sleep: a brawl outside is what a person reacts to whatever the hour, and
     * a town that goes on hoeing and hawking through one reads as scenery rather than as people.
     *
     * They press into the doorway rather than going through it, which is [Goals.SLEEP]'s trick and is
     * deliberately *not* reused here. Going inside means being destroyed and remembered by
     * [IndoorRegistry], and the registry brings people back out on the clock - so shelter would have to
     * teach it a second kind of deadline, when the thing being waited for is a fight ending. Cowering in
     * plain sight also tells the player what they did, which vanishing does not.
     *
     * Availability wants a door as well as a threat, for [WORK_SHIFT]'s reason: `AiThinkSystem` plans for
     * the top goal only, so a threatened villager with nowhere to run would freeze on the spot rather
     * than carry on. See [SHELTERED] for why the desired state is a flag nothing ever sets.
     */
    val TAKE_SHELTER = Goal(
      name = "TakeShelter",
      priority = priority(base = 100f),
      availability = Precondition { s ->
        s.get(THREAT_POSITION) != null && s.get(SHELTER_DOOR) != null && !holdsGround(s)
      },
      desiredState = listOf(Preconditions.equalTo(SHELTERED, true)),
    )

    /**
     * A meal, once hunger says so.
     *
     * A hungry villager breaks off what they are doing and walks to the vendor, which is the behaviour
     * the whole chain exists to show - but a fight outside still outranks lunch.
     *
     * The base looks far higher than [WORK_SHIFT]'s and is not, which is the trap in scaled priorities:
     * a consideration *multiplies* the base, so ninety-five at seven-tenths hungry scores sixty-six and
     * loses to a flat seventy-five. That is the wanted shape - work through peckish, break off when
     * genuinely hungry - but it has to be arrived at deliberately rather than by reading the bases.
     *
     * Availability wants somewhere to buy as well as an appetite, for [WORK_SHIFT]'s reason. It is
     * also what keeps hunger from being a drive nobody can spend: in a hamlet with no counter the goal
     * is simply never available, rather than being chosen and unplannable.
     */
    val EAT = Goal(
      name = "Eat",
      priority = priority(base = 95f, combine = Combine.MAX) {
        consider(HUNGER.linear())
      },
      availability = Precondition { s ->
        (s.get(HUNGER) ?: 0) >= (s.get(HUNGER_THRESHOLD) ?: DEFAULT_HUNGER_THRESHOLD) && canEat(s)
      },
      desiredState = listOf(Preconditions.atMost(HUNGER, FED_HUNGER)),
    )

    /**
     * Fetching the day's stock from whoever makes it.
     *
     * The longest plan the domain has, and the only one the planner has to *chain* rather than pick: it
     * costs four actions to get from an empty counter to a full one, and no single action reaches the
     * goal. Nothing enumerates that sequence anywhere - `goToSupplier -> collectStock -> goToWork ->
     * deliverStock` falls out of the preconditions, which is the whole argument for planning over a
     * scripted routine.
     *
     * Above [WORK_SHIFT], so the errand is run before settling in behind the counter rather than after the
     * shift has been marked done for the day.
     *
     * Availability wants stock at the other end as well as a supplier, and that is the same guard
     * [WORK_SHIFT] carries: a goal nothing can plan freezes the agent outright, and "the bakery is empty"
     * is exactly the state a player creates by burning the fields.
     */
    val RESTOCK = Goal(
      name = "Restock",
      priority = priority(base = 78f),
      availability = Precondition { s ->
        s.get(WORK_POSITION) != null && isOnShift(s) && canRestock(s) && !hasRestockedToday(s)
      },
      desiredState = listOf(Precondition { s -> hasRestockedToday(s) }),
    )

    /**
     * The evening, spent somewhere other than alone at home.
     *
     * Above [GO_HOME] for [WORK_SHIFT]'s reason, which is not a preference but a requirement: the inn is
     * further from a person's door than the loiter radius, so a lower-ranked evening would be overruled
     * the moment they got there and they would be walked home again a few paces at a time.
     *
     * Satisfied by a stamp rather than by arriving, also for [WORK_SHIFT]'s reason. Satisfied on arrival,
     * the goal would drop the moment it was met, [GO_HOME] would take over, and the evening would become
     * a shuttle between the door and the inn.
     *
     * There is no drive behind it on purpose. Sociability would be a fourth number to tune against three
     * that already interact, and the hour says the same thing - a square fills up in the evening and
     * empties at bedtime whether or not anybody is keeping score.
     */
    val SOCIALISE = Goal(
      name = "Socialise",
      priority = priority(base = 72f),
      availability = Precondition { s ->
        isEvening(s) && s.get(SOCIAL_POSITION) != null && !hasSocialisedToday(s)
      },
      desiredState = listOf(Precondition { s -> hasSocialisedToday(s) }),
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
      EAT,
      GO_HOME,
      LOITER,
      RESTOCK,
      SLEEP,
      SOCIALISE,
      TAKE_SHELTER,
      WORK_SHIFT,
    )

    val BY_NAME = ALL.associateBy { it.name }
  }

  /** What a template needs beyond the planning contract. */
  data class Collaborators(
    val locomotion: Locomotion,
    val indoors: IndoorRegistry,
    val work: SettlementWork,
    val production: TownsfolkProduction,
  )

  private val TEMPLATE_FACTORIES: Map<String, (Collaborators) -> ActionTemplate> = mapOf(
    "buyFood" to { _ -> BuyFoodActionTemplate() },
    "eat" to { _ -> EatActionTemplate() },
    "goHome" to { c -> GoHomeActionTemplate(c.locomotion) },
    "goToMeal" to { c -> GoToMealActionTemplate(c.locomotion) },
    "goToWork" to { c -> GoToWorkActionTemplate(c.locomotion) },
    "goToShelter" to { c -> GoToShelterActionTemplate(c.locomotion) },
    "goToGathering" to { c -> GoToGatheringActionTemplate(c.locomotion) },
    "mingle" to { _ -> MingleActionTemplate() },
    "goToSupplier" to { c -> GoToSupplierActionTemplate(c.locomotion) },
    "collectStock" to { _ -> CollectStockActionTemplate() },
    "deliverStock" to { _ -> DeliverStockActionTemplate() },
    "loiter" to { c -> LoiterActionTemplate(c.locomotion) },
    "shelterAtDoor" to { _ -> ShelterAtDoorActionTemplate() },
    "enterHome" to { c -> EnterHomeActionTemplate(c.indoors) },
    "sleepAtHome" to { _ -> SleepAtHomeActionTemplate() },
    "workShift" to { c -> WorkShiftActionTemplate(c.work, c.production) },
  )

  override val actionIds: Set<String> get() = TEMPLATE_FACTORIES.keys

  override val goalsByName: Map<String, Goal> get() = Goals.BY_NAME

  fun resolver(actionIds: List<String>, collaborators: Collaborators): ActionResolver {
    val catalog = TEMPLATE_FACTORIES.mapValues { (_, build) -> build(collaborators) }
    return CompositeActionResolver(actionIds.mapNotNull { catalog[it] })
  }
}
