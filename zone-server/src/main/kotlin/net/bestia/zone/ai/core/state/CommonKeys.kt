package net.bestia.zone.ai.core.state

import net.bestia.zone.geometry.Vec3L

/**
 * The keys the engine's own systems write, and therefore the ones every domain shares.
 *
 * The rule is mechanical rather than a matter of taste: a key belongs here when perception, the drive
 * system or the agent factory writes it, because those run over every agent in the world and cannot ask a
 * domain what it calls things. A key a domain writes for itself - what a creature knows about vegetation,
 * what hours a shop keeps - stays in that domain.
 *
 * Sharing them is about safety, not about reuse. Keys are equal by name, so two domains declaring
 * `StateKey<Vec3L>("position")` already address one slot whether or not they mean to. What differs is the
 * metadata: declare the same name with the wrong type and [WorldState.get]'s unchecked cast fails at
 * whichever reader is unluckiest, and declare it without [StateKey.observed] and the planner will happily
 * write a merely *imagined* nightfall into live memory. One declaration removes both.
 */
object CommonKeys {

  // ----------------------------------------------------------------- observations

  val POSITION = StateKey<Vec3L>("position", observed = true, retain = Blackboard.PERMANENT)

  /** Own health as a 0..100 percentage, so a utility curve can read it like any other stat. */
  val HEALTH_PCT = StateKey<Int>("healthPct", observed = true, retain = Blackboard.PERMANENT)

  val ENEMY_IN_SIGHT = StateKey<Boolean>("enemyInSight", observed = true)
  val TARGET_ID = StateKey<Long>("targetId", observed = true)
  val TARGET_ARCHETYPE = StateKey<String>("targetArchetype", observed = true)
  val TARGET_POSITION = StateKey<Vec3L>("targetPosition", observed = true)

  /** Flipped true by perception when this agent is attacked, gating retaliation. */
  val IS_AGGRO = StateKey<Boolean>("isAggro", observed = true)

  val IS_NIGHT = StateKey<Boolean>("isNight", observed = true, retain = Blackboard.PERMANENT)

  /**
   * The world calendar's minute of the day, 0..1439.
   *
   * [IS_NIGHT] is too coarse for anything with a timetable: full night runs 22:00 to 04:00, while a baker
   * opens at five and a guard's watch ends at six. Minutes rather than hours because a timetable everybody
   * keeps to the hour is one a whole town acts on in the same step - see [DAY_OFFSET_MINUTES].
   */
  val MINUTE_OF_DAY = StateKey<Int>("minuteOfDay", observed = true, retain = Blackboard.PERMANENT)

  /**
   * How far this individual's own day sits off the hours their occupation states, in minutes.
   *
   * Absent for anybody who keeps the stated hours, which is every creature and every agent a GM puts
   * down by hand. See [net.bestia.zone.ai.core.state.HourWindow.coversMinute] for why one number covers
   * the whole day rather than one per boundary.
   */
  val DAY_OFFSET_MINUTES = StateKey<Int>("dayOffsetMinutes", retain = Blackboard.PERMANENT)

  /**
   * Days since the world began, whole.
   *
   * What makes "already done today" expressible. A goal that must happen once a day stamps this and
   * compares against it, and midnight clears the stamp by moving on - no writer has to remember to.
   */
  val DAY_INDEX = StateKey<Long>("dayIndex", observed = true, retain = Blackboard.PERMANENT)

  // ---------------------------------------------------------------- profile knobs

  /** Spawn tile, written once when a profile is attached. */
  val HOME_POSITION = StateKey<Vec3L>("homePosition", retain = Blackboard.PERMANENT)

  val WANDER_RADIUS = StateKey<Long>("wanderRadius", retain = Blackboard.PERMANENT)
  val HUNGER_THRESHOLD = StateKey<Int>("hungerThreshold", retain = Blackboard.PERMANENT)
  val TIREDNESS_THRESHOLD = StateKey<Int>("tirednessThreshold", retain = Blackboard.PERMANENT)
  val RESTLESS_THRESHOLD = StateKey<Int>("restlessThreshold", retain = Blackboard.PERMANENT)

  // --------------------------------------------------------------------- beliefs

  val HUNGER = StateKey<Int>("hunger", retain = Blackboard.PERMANENT)
  val TIREDNESS = StateKey<Int>("tiredness", retain = Blackboard.PERMANENT)

  /** Builds up while nothing else is worth doing and is spent by ambling about. */
  val RESTLESSNESS = StateKey<Int>("restlessness", retain = Blackboard.PERMANENT)

  /**
   * Has slept out whatever made it want to.
   *
   * Set by sleeping and cleared by perception for as long as the agent's [RestingWindow] lasts, which is
   * what keeps a sleep goal *unsatisfied* through a night rather than met the moment it lies down.
   */
  val RESTED = StateKey<Boolean>("rested", retain = Blackboard.PERMANENT)

  /**
   * Whatever this agent was fighting is dead.
   *
   * Here for [RESTED]'s reason rather than because combat is common: perception is what clears it, on the
   * same pass that decides who the target is. Permanent because that clearer is what ends it - left to a
   * timer, a kill goal stays satisfied and the agent will not defend itself until the belief expires.
   */
  val TARGET_DEAD = StateKey<Boolean>("targetDead", retain = Blackboard.PERMANENT)
}
