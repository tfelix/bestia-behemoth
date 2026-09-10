package net.bestia.zone.ai.domain.bestia

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
import net.bestia.zone.ai.core.state.RestingWindow
import net.bestia.zone.ai.core.state.MemoryScope
import net.bestia.zone.ai.core.state.StateKey
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.AiDomainCatalogue
import net.bestia.zone.ai.domain.bestia.action.ApproachTargetActionTemplate
import net.bestia.zone.ai.domain.bestia.action.AttackActionTemplate
import net.bestia.zone.ai.domain.bestia.action.EatVegetationActionTemplate
import net.bestia.zone.ai.domain.bestia.action.ReturnHomeActionTemplate
import net.bestia.zone.ai.domain.bestia.action.SleepActionTemplate
import net.bestia.zone.ai.domain.bestia.action.WalkToVegetationActionTemplate
import net.bestia.zone.ai.domain.bestia.action.WanderActionTemplate
import net.bestia.zone.battle.skill.AttackExecutionService
import net.bestia.zone.battle.skill.SkillExecutionService
import net.bestia.zone.geometry.Vec3L

/**
 * The concrete GOAP domain for bestia mobs: state keys, the goal catalogue, and the catalogue of
 * [ActionTemplate]s that ground them. Built on top of the domain-agnostic `ai/core` the same way
 * `net.bestia.zone.ai.core.MarketDomain` demonstrates it for the villager scenario, but composed from
 * small, independently testable templates rather than one big resolver closure.
 *
 * ### Who writes what
 *
 * Keys divide into three groups, and keeping them straight is what stops the AI from believing things
 * that never happened:
 *
 *  - **Observations** ([POSITION], [HEALTH_PCT], [ENEMY_IN_SIGHT], [TARGET_POSITION], [IS_NIGHT], ...) are
 *    written only by the perception system, from the real world. No action's effect may claim them.
 *  - **Profile knobs** ([WANDER_RADIUS], [HUNGER_THRESHOLD], [AGGRESSION], [ACTIVITY_CYCLE], ...) are written
 *    once, permanently, when a profile is attached, and read by goal availability and priority.
 *  - **Beliefs** ([KNOWN_VEGETATION], [ATTACK_EFFECTIVENESS], [TARGET_DEAD], [RESTED], and the drives
 *    [HUNGER]/[TIREDNESS]/[RESTLESSNESS]) are what an action's effects may update, and only once that
 *    action's behaviour tree has actually reported success. Perception may *clear* a belief its observations
 *    contradict — that is how [RESTED] and [TARGET_DEAD] end — but it never asserts one.
 *
 * The planner still simulates effects over observation keys during A* — `walkTo(spot)` has to be able
 * to imagine standing on the spot, or no plan involving movement could ever be found. The rule is about
 * what gets *written back* to live memory afterwards, not about what the search may hypothesise.
 */
object BestiaDomain : AiDomainCatalogue {

  /** What a profile's `domain:` names to get this one. See [net.bestia.zone.ai.domain.AiDomains]. */
  const val ID = "bestia"

  override val id = ID

  /** Grid tiles counted as "arrived" — [net.bestia.zone.geometry.Vec3L.distance] is exact tile distance. */
  const val ARRIVAL_RADIUS = 1L
  /**
   * Tiles a creature may stray from its home before [Goals.RETURN_HOME] pulls it back.
   *
   * A home range, not a tether. At the five tiles this was, a creature scattered once by its den stood
   * within a few paces of that spot for the rest of its life, so a pack stayed the knot it was seeded as and
   * no amount of density made the country look inhabited. It has to be at least comparable to the spacing
   * between creatures, or neighbouring ranges never overlap and the field reads as pinned scenery.
   *
   * Stride is a separate question - see [net.bestia.zone.ai.bt.Locomotion.WANDER_STEP_TILES].
   */
  const val DEFAULT_WANDER_RADIUS = 24L
  const val DEFAULT_MELEE_RANGE = 1L
  const val DEFAULT_RESTLESS_THRESHOLD = 60

  /**
   * Tiredness at or below which a creature counts as rested.
   *
   * Shared between [Goals.SLEEP]'s desired state and the sleep behaviour's own "am I done" test on purpose:
   * two copies of this number would let a creature stop sleeping while its goal still considered it tired,
   * or the reverse, and both read as a mob twitching in and out of bed.
   */
  const val RESTED_TIREDNESS = 20

  // ------------------------------------------------------------ shared with every domain

  // Re-exported rather than redeclared. Keys are equal by name, so these already addressed the same slots
  // as `CommonKeys`; aliasing makes that visible and leaves one declaration to get the metadata right.
  val POSITION = CommonKeys.POSITION
  val HEALTH_PCT = CommonKeys.HEALTH_PCT
  val ENEMY_IN_SIGHT = CommonKeys.ENEMY_IN_SIGHT
  val TARGET_ID = CommonKeys.TARGET_ID
  val TARGET_ARCHETYPE = CommonKeys.TARGET_ARCHETYPE
  val TARGET_POSITION = CommonKeys.TARGET_POSITION
  val IS_AGGRO = CommonKeys.IS_AGGRO
  val IS_NIGHT = CommonKeys.IS_NIGHT
  val HOME_POSITION = CommonKeys.HOME_POSITION
  val WANDER_RADIUS = CommonKeys.WANDER_RADIUS
  val HUNGER_THRESHOLD = CommonKeys.HUNGER_THRESHOLD
  val TIREDNESS_THRESHOLD = CommonKeys.TIREDNESS_THRESHOLD
  val RESTLESS_THRESHOLD = CommonKeys.RESTLESS_THRESHOLD
  val HUNGER = CommonKeys.HUNGER
  val TIREDNESS = CommonKeys.TIREDNESS
  val RESTLESSNESS = CommonKeys.RESTLESSNESS
  val RESTED = CommonKeys.RESTED
  val TARGET_DEAD = CommonKeys.TARGET_DEAD

  // ------------------------------------------------------------------ this domain

  val MELEE_RANGE = StateKey<Long>("meleeRange", retain = Blackboard.PERMANENT)

  /** 0..100 temperament knob; scales how strongly the kill goals are wanted. */
  val AGGRESSION = StateKey<Int>("aggression", retain = Blackboard.PERMANENT)

  /** When this species sleeps, against the world's day/night cycle. See [isRestingPhase]. */
  val ACTIVITY_CYCLE = StateKey<ActivityCycle>("activityCycle", retain = Blackboard.PERMANENT)


  /** Shared pack-wide: one bestia's foraging discovery becomes every packmate's knowledge. */
  val KNOWN_VEGETATION =
    StateKey<List<VegetationMemory>>("knownVegetation", MemoryScope.TEAM, retain = Blackboard.PERMANENT)

  /** Shared world-wide: "fire hurts golems" is knowledge the whole species can learn once. */
  val ATTACK_EFFECTIVENESS =
    StateKey<Map<EffectivenessKey, Double>>("attackEffectiveness", MemoryScope.WORLD, retain = Blackboard.PERMANENT)

  /**
   * What a creature's body does to it while nothing else is happening.
   *
   * Per in-game hour. These are the long-standing per-real-second rates - peckish in about three real
   * minutes, sleepy in seven, bored in one - restated in the unit a day is measured in, at the shipped
   * speed factor of three. Tiredness runs backwards while asleep, and twenty times as fast, so a full
   * night is slept off well before dawn; an interrupted night therefore means something, because the
   * recovery is continuous rather than a jump when the sleeping finishes.
   */
  val DRIVES = listOf(
    Drive(HUNGER, perGameHour = 660f),
    Drive(TIREDNESS, perGameHour = 300f, whileSleepingPerGameHour = -6_000f),
    Drive(RESTLESSNESS, perGameHour = 1_920f),
  )

  /** A species sleeps by the sun, so its window is whatever its [ActivityCycle] calls resting. */
  fun restingWindow(cycle: ActivityCycle): RestingWindow {
    return RestingWindow { _, isNight -> cycle.isRestingAt(isNight) }
  }

  // ------------------------------------------------------------------- helpers

  internal fun distanceOrMax(a: Vec3L?, b: Vec3L?): Long =
    if (a == null || b == null) Long.MAX_VALUE else a.distance(b)

  /**
   * Whether it is this creature's bedtime: the world's [IS_NIGHT] read through its own [ACTIVITY_CYCLE].
   *
   * Two overloads because the two readers genuinely differ. Goals ask against the frozen [WorldState] the
   * plan was made from; the sleep behaviour asks against the agent's *live* memory, because "is it still
   * night" is a question about now rather than about the moment the decision was taken — a creature that
   * consulted the snapshot would sleep until dawn according to a sky it saw hours ago.
   */
  fun isRestingPhase(state: WorldState): Boolean =
    isRestingPhase(state.get(ACTIVITY_CYCLE), state.get(IS_NIGHT))

  fun isRestingPhase(memory: Blackboard): Boolean =
    isRestingPhase(memory.get(ACTIVITY_CYCLE), memory.get(IS_NIGHT))

  /**
   * Unknown reads as "not resting", never as resting: an agent perception has not looked at yet has no idea
   * what time it is, and a creature must not lie down on the strength of an observation nobody has made —
   * the same rule [homeDistanceOrNull] exists for.
   */
  private fun isRestingPhase(cycle: ActivityCycle?, night: Boolean?): Boolean =
    night != null && (cycle ?: ActivityCycle.CATHEMERAL).isRestingAt(night)

  /**
   * Distance from home, or null when either position is unknown.
   *
   * Null rather than a sentinel on purpose. It used to fall back to [Long.MAX_VALUE], which made a
   * *brand-new* agent — one perception has not looked at yet, so with no position in memory — appear to be
   * infinitely far from home, so the very first thing every creature wanted to do was go home. A goal must
   * not be available on the strength of an observation nobody has made.
   */
  private fun homeDistanceOrNull(state: WorldState): Long? {
    val position = state.get(POSITION) ?: return null
    val home = state.get(HOME_POSITION) ?: return null
    return position.distance(home)
  }

  private fun wanderRadiusOf(state: WorldState): Long = state.get(WANDER_RADIUS) ?: DEFAULT_WANDER_RADIUS

  private fun healthPctOf(state: WorldState): Int = state.get(HEALTH_PCT) ?: 100

  private fun enemyInSight(state: WorldState): Boolean = state.get(ENEMY_IN_SIGHT) == true

  /**
   * How badly sleeping is wanted purely because of the hour, independent of how tired the creature is.
   *
   * The value is chosen against the other goals' bases rather than picked for feel: at 0.9 of [Goals.SLEEP]'s
   * base it outranks a starving creature's [Goals.EAT_VEGETATION] (80 at its maximum) but stays under
   * [Goals.KILL_ATTACKER] (95) — so a diurnal animal sleeps the night through rather than grazing in the
   * dark, and still wakes up the moment something bites it.
   */
  private val restingPhaseUrgency = Curve { state -> if (isRestingPhase(state)) 0.9 else 0.0 }

  object Goals {

    val EAT_VEGETATION = Goal(
      name = "EatVegetation",
      priority = priority(base = 80f) { consider(HUNGER.linear()) },
      availability = Precondition { s -> (s.get(HUNGER) ?: 0) >= (s.get(HUNGER_THRESHOLD) ?: 85) },
      desiredState = listOf(Preconditions.atMost(HUNGER, 15)),
    )

    /**
     * Sleeping, for either of the two reasons a creature does it: because it is worn out, or because it is
     * that creature's night.
     *
     * [Combine.MAX] rather than the default mean, and that is what keeps the second reason from diluting the
     * first. Averaged, a wide-awake animal at bedtime would score `(0 + 0.9) / 2`, and an exhausted one at
     * noon `(0.95 + 0) / 2` — both halved by the consideration that does not apply. Taking the maximum reads
     * as "whichever reason is pressing", and for an [ActivityCycle.CATHEMERAL] archetype (every profile that
     * existed before activity cycles did) [restingPhaseUrgency] is flat zero, so the maximum is the tiredness
     * term and nothing about those profiles changes.
     *
     * The desired state needs both conditions for the same reason: a tiredness ceiling alone is already met
     * by a rested creature, so at nightfall the goal would count as satisfied and be skipped. See [RESTED].
     */
    val SLEEP = Goal(
      name = "Sleep",
      priority = priority(base = 90f, combine = Combine.MAX) {
        consider(TIREDNESS.linear())
        consider(restingPhaseUrgency)
      },
      availability = Precondition { s ->
        (s.get(TIREDNESS) ?: 0) >= (s.get(TIREDNESS_THRESHOLD) ?: 80) || isRestingPhase(s)
      },
      desiredState = listOf(
        Preconditions.atMost(TIREDNESS, RESTED_TIREDNESS),
        Preconditions.equalTo(RESTED, true),
      ),
    )

    /**
     * Fires once the bestia has wandered further than its [WANDER_RADIUS] from [HOME_POSITION].
     * Unlike the drive-based goals this isn't gated on a decaying resource but on a live distance check
     * over two positional keys at once, which is why its availability is a raw [Precondition] lambda
     * rather than one of the single-key [Preconditions] helpers.
     */
    val RETURN_HOME = Goal(
      name = "ReturnHome",
      priority = priority(base = 70f),
      // Unknown distance means unavailable, never "infinitely far" — see homeDistanceOrNull.
      availability = Precondition { s -> (homeDistanceOrNull(s) ?: 0L) > wanderRadiusOf(s) },
      desiredState = listOf(Precondition { s -> (homeDistanceOrNull(s) ?: Long.MAX_VALUE) <= wanderRadiusOf(s) }),
    )

    /**
     * Retaliation: gated purely on [IS_AGGRO], which perception flips true when this bestia is hit.
     * Available to any archetype, however peaceful — being attacked is not something a profile opts
     * into.
     *
     * Since nothing runs away any more, this is the *whole* of what being attacked provokes, and its base
     * outranks every other goal on purpose: a creature that is being hit fights back rather than wandering
     * off to graze or lying down to sleep mid-fight. It stays available however badly hurt — there is no
     * health floor below which a cornered animal stops defending itself.
     */
    val KILL_ATTACKER = Goal(
      name = "KillAttacker",
      priority = priority(base = 95f),
      availability = Preconditions.equalTo(IS_AGGRO, true),
      desiredState = listOf(Preconditions.equalTo(TARGET_DEAD, true)),
    )

    /**
     * Unprovoked aggression: attack whatever hostile is in sight. Only archetypes that list it are
     * aggressive on sight, which is what separates a wolf from a deer.
     *
     * Availability is the bare sighting, with no health floor under it. It used to also require *not* being
     * wounded, which paired off against a flee goal enabled by the same threshold — exactly one of the two
     * was ever available, so a hurt predator broke off and ran instead of charging. With fleeing gone that
     * gate had nothing to hand over to: it would have left a wounded hunter with its target in plain sight
     * and no goal about it at all, standing still until something else became available.
     *
     * [HEALTH_PCT] survives as a *consideration* rather than a gate, which is the part of the old behaviour
     * worth keeping: a badly hurt aggressor still wants the kill less than a healthy one, so it will drop a
     * fight it merely picked in favour of eating or sleeping. Being attacked is a different question and
     * [KILL_ATTACKER] answers it, unscaled.
     */
    val KILL_ENEMY = Goal(
      name = "KillEnemy",
      priority = priority(base = 85f) {
        consider(AGGRESSION.linear())
        consider(HEALTH_PCT.linear())
      },
      availability = Precondition { s -> enemyInSight(s) },
      desiredState = listOf(Preconditions.equalTo(TARGET_DEAD, true)),
    )

    /**
     * Idle ambling, an ordinary goal thanks to [RESTLESSNESS]. Lowest base priority, so anything with a
     * real drive behind it wins.
     */
    val WANDER = Goal(
      name = "Wander",
      priority = priority(base = 20f) { consider(RESTLESSNESS.linear()) },
      availability = Precondition { s ->
        (s.get(RESTLESSNESS) ?: 0) >= (s.get(RESTLESS_THRESHOLD) ?: DEFAULT_RESTLESS_THRESHOLD)
      },
      desiredState = listOf(Preconditions.atMost(RESTLESSNESS, 20)),
    )

    val ALL = listOf(EAT_VEGETATION, SLEEP, RETURN_HOME, KILL_ATTACKER, KILL_ENEMY, WANDER)

    val BY_NAME = ALL.associateBy { it.name }
  }

  /**
   * What a template needs beyond the planning contract, bundled so adding a collaborator does not widen
   * eight lambdas. Two services rather than one because a bestia can both bite and cast, and those are
   * different pathways: see [AttackExecutionService] and [SkillExecutionService].
   */
  data class Collaborators(
    val locomotion: Locomotion,
    val skills: SkillExecutionService,
    val attackExecution: AttackExecutionService,
    val attacks: List<AttackDefinition> = emptyList(),
  )

  /**
   * How to build each template this domain knows, keyed by the id a profile names it with.
   *
   * Templates need real collaborators now that they carry behaviour as well as a planning contract, so this
   * is a map of *factories* rather than of instances. Keeping it as one map means [actionIds] and
   * [actionTemplates] cannot drift apart, which a hand-maintained second list of ids inevitably would.
   */
  private val TEMPLATE_FACTORIES: Map<String, (Collaborators) -> ActionTemplate> = mapOf(
    "wander" to { c -> WanderActionTemplate(c.locomotion) },
    "returnHome" to { c -> ReturnHomeActionTemplate(c.locomotion) },
    "walkToVegetation" to { c -> WalkToVegetationActionTemplate(c.locomotion) },
    "eatVegetation" to { _ -> EatVegetationActionTemplate() },
    "sleep" to { _ -> SleepActionTemplate() },
    "approachTarget" to { c -> ApproachTargetActionTemplate(c.locomotion) },
    "attack" to { c -> AttackActionTemplate(c.attacks, c.skills, c.attackExecution) },
  )

  /** Every action id a profile may name, for fail-fast validation at boot without building anything. */
  override val actionIds: Set<String> get() = TEMPLATE_FACTORIES.keys

  override val goalsByName: Map<String, Goal> get() = Goals.BY_NAME

  /** Every action template this domain knows, keyed by [ActionTemplate.id] for profile lookups. */
  fun actionTemplates(collaborators: Collaborators): Map<String, ActionTemplate> =
    TEMPLATE_FACTORIES.mapValues { (_, build) -> build(collaborators) }

  /** Builds the [ActionResolver] for a profile's declared [actionIds]. */
  fun resolver(actionIds: List<String>, collaborators: Collaborators): ActionResolver {
    val catalog = actionTemplates(collaborators)
    return CompositeActionResolver(actionIds.mapNotNull { catalog[it] })
  }
}
