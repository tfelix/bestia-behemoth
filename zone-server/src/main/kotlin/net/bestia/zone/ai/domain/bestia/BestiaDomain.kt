package net.bestia.zone.ai.domain.bestia

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.core.action.ActionResolver
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.action.CompositeActionResolver
import net.bestia.zone.ai.core.goal.Curve
import net.bestia.zone.ai.core.goal.Goal
import net.bestia.zone.ai.core.goal.linear
import net.bestia.zone.ai.core.goal.priority
import net.bestia.zone.ai.core.precondition.Precondition
import net.bestia.zone.ai.core.precondition.Preconditions
import net.bestia.zone.ai.core.state.MemoryScope
import net.bestia.zone.ai.core.state.StateKey
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.bestia.action.ApproachTargetActionTemplate
import net.bestia.zone.ai.domain.bestia.action.AttackActionTemplate
import net.bestia.zone.ai.domain.bestia.action.EatVegetationActionTemplate
import net.bestia.zone.ai.domain.bestia.action.FleeActionTemplate
import net.bestia.zone.ai.domain.bestia.action.ReturnHomeActionTemplate
import net.bestia.zone.ai.domain.bestia.action.SleepActionTemplate
import net.bestia.zone.ai.domain.bestia.action.WalkToVegetationActionTemplate
import net.bestia.zone.ai.domain.bestia.action.WanderActionTemplate
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
 *  - **Observations** ([POSITION], [HEALTH_PCT], [ENEMY_IN_SIGHT], [TARGET_POSITION], ...) are written
 *    only by the perception system, from the real world. No action's effect may claim them.
 *  - **Profile knobs** ([WANDER_RADIUS], [HUNGER_THRESHOLD], [AGGRESSION], ...) are written once,
 *    permanently, when a profile is attached, and read by goal availability and priority.
 *  - **Beliefs** ([KNOWN_VEGETATION], [ATTACK_EFFECTIVENESS], [TARGET_DEAD], [SAFE], and the drives
 *    [HUNGER]/[TIREDNESS]/[RESTLESSNESS]) are what an action's effects may update, and only once that
 *    action's behaviour tree has actually reported success.
 *
 * The planner still simulates effects over observation keys during A* — `walkTo(spot)` has to be able
 * to imagine standing on the spot, or no plan involving movement could ever be found. The rule is about
 * what gets *written back* to live memory afterwards, not about what the search may hypothesise.
 */
object BestiaDomain {

  /** Grid tiles counted as "arrived" — [net.bestia.zone.geometry.Vec3L.distance] is exact tile distance. */
  const val ARRIVAL_RADIUS = 1L
  const val DEFAULT_WANDER_RADIUS = 5L
  const val DEFAULT_MELEE_RANGE = 1L
  const val DEFAULT_FLEE_THRESHOLD_PCT = 35
  const val DEFAULT_RESTLESS_THRESHOLD = 60

  /** How far from a threat counts as having escaped it. */
  const val SAFE_DISTANCE = 12L

  // ---------------------------------------------------------------- observations

  val POSITION = StateKey<Vec3L>("position", observed = true)

  /** Own health as a 0..100 percentage, so the utility curves can read it like any other stat. */
  val HEALTH_PCT = StateKey<Int>("healthPct", observed = true)

  val ENEMY_IN_SIGHT = StateKey<Boolean>("enemyInSight", observed = true)
  val TARGET_ID = StateKey<Long>("targetId", observed = true)
  val TARGET_ARCHETYPE = StateKey<String>("targetArchetype", observed = true)
  val TARGET_POSITION = StateKey<Vec3L>("targetPosition", observed = true)
  val THREAT_POSITION = StateKey<Vec3L>("threatPosition", observed = true)

  /** Flipped true by perception when this bestia is attacked, gating retaliation. */
  val IS_AGGRO = StateKey<Boolean>("isAggro", observed = true)

  // --------------------------------------------------------------- profile knobs

  /** Spawn tile, written once (permanently) when a profile is attached. */
  val HOME_POSITION = StateKey<Vec3L>("homePosition")
  val WANDER_RADIUS = StateKey<Long>("wanderRadius")
  val MELEE_RANGE = StateKey<Long>("meleeRange")
  val HUNGER_THRESHOLD = StateKey<Int>("hungerThreshold")
  val TIREDNESS_THRESHOLD = StateKey<Int>("tirednessThreshold")

  /** Health percentage at or below which fleeing becomes available. */
  val FLEE_THRESHOLD_PCT = StateKey<Int>("fleeThresholdPct")

  /** Restlessness at or above which idle wandering becomes available. */
  val RESTLESS_THRESHOLD = StateKey<Int>("restlessThreshold")

  /** 0..100 temperament knob; scales how strongly the kill goals are wanted. */
  val AGGRESSION = StateKey<Int>("aggression")

  // -------------------------------------------------------------------- beliefs

  val HUNGER = StateKey<Int>("hunger")
  val TIREDNESS = StateKey<Int>("tiredness")

  /**
   * Builds up while nothing else is worth doing and is spent by wandering.
   *
   * This key is what lets idling be an ordinary goal. A "keep wandering" goal has no naturally
   * unsatisfied state — its desired state either holds before any step is taken, so the planner (which
   * only selects goals that are *not* already satisfied) would never pick it, or holds forever after one
   * step, so it would never run again. The previous code worked around that with a reflexive
   * `fallbackWander` escape hatch outside the goal system entirely. Giving idleness a decaying drive,
   * exactly like hunger and tiredness, removes the special case: restlessness rises, the wander goal
   * becomes available and unsatisfied, wandering spends it, and it rises again.
   */
  val RESTLESSNESS = StateKey<Int>("restlessness")

  /** Believed-safe. Set by fleeing, cleared by perception the moment a hostile is in sight again. */
  val SAFE = StateKey<Boolean>("safe")

  val TARGET_DEAD = StateKey<Boolean>("targetDead")

  /** Shared pack-wide: one bestia's foraging discovery becomes every packmate's knowledge. */
  val KNOWN_VEGETATION = StateKey<List<VegetationMemory>>("knownVegetation", MemoryScope.TEAM)

  /** Shared world-wide: "fire hurts golems" is knowledge the whole species can learn once. */
  val ATTACK_EFFECTIVENESS =
    StateKey<Map<EffectivenessKey, Double>>("attackEffectiveness", MemoryScope.WORLD)

  // ------------------------------------------------------------------- helpers

  internal fun distanceOrMax(a: Vec3L?, b: Vec3L?): Long =
    if (a == null || b == null) Long.MAX_VALUE else a.distance(b)

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

  private fun fleeThresholdOf(state: WorldState): Int = state.get(FLEE_THRESHOLD_PCT) ?: DEFAULT_FLEE_THRESHOLD_PCT

  private fun healthPctOf(state: WorldState): Int = state.get(HEALTH_PCT) ?: 100

  private fun enemyInSight(state: WorldState): Boolean = state.get(ENEMY_IN_SIGHT) == true

  /** Hurt enough that self-preservation should be on the table. */
  private fun isWounded(state: WorldState): Boolean = healthPctOf(state) <= fleeThresholdOf(state)

  /**
   * How badly fleeing is wanted, measured *relative to this archetype's own threshold* rather than against
   * absolute health: 0.6 the moment it becomes wounded, rising to 1.0 at death.
   *
   * A plain `HEALTH_PCT.inverseLinear()` cannot work here, because the threshold is per-archetype. A critter
   * that runs at 80% health would get an urgency of only 0.2 from an absolute curve and would go on trading
   * blows, while a hardened predator that runs at 20% would get 0.8 — the same wound producing opposite
   * behaviour purely because the archetypes disagree about when to worry.
   *
   * The floor of 0.6 exists so that whenever fleeing is available at all it outranks retaliating. Availability
   * is where the flight-or-fight decision is made; priority only orders what is already on the table, and a
   * creature that has decided it is losing should not be talked back into the fight by arithmetic.
   */
  private val fleeUrgency = Curve { state ->
    val threshold = fleeThresholdOf(state).coerceAtLeast(1)
    val health = healthPctOf(state).coerceIn(0, threshold)
    0.6 + 0.4 * (1.0 - health.toDouble() / threshold)
  }

  object Goals {

    val EAT_VEGETATION = Goal(
      name = "EatVegetation",
      priority = priority(base = 80f) { consider(HUNGER.linear()) },
      availability = Precondition { s -> (s.get(HUNGER) ?: 0) >= (s.get(HUNGER_THRESHOLD) ?: 85) },
      desiredState = listOf(Preconditions.atMost(HUNGER, 15)),
    )

    val SLEEP = Goal(
      name = "Sleep",
      priority = priority(base = 90f) { consider(TIREDNESS.linear()) },
      availability = Precondition { s -> (s.get(TIREDNESS) ?: 0) >= (s.get(TIREDNESS_THRESHOLD) ?: 80) },
      desiredState = listOf(Preconditions.atMost(TIREDNESS, 20)),
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
     */
    val KILL_ATTACKER = Goal(
      name = "KillAttacker",
      priority = priority(base = 95f),
      availability = Preconditions.equalTo(IS_AGGRO, true),
      desiredState = listOf(Preconditions.equalTo(TARGET_DEAD, true)),
    )

    /**
     * Unprovoked aggression: attack whatever hostile is in sight, as long as not already too hurt to
     * be picking fights. Only archetypes that list it are aggressive on sight, which is what separates
     * a wolf from a deer.
     *
     * Scaled by [AGGRESSION] and by current health, so a wounded aggressor loses interest before it
     * gets itself killed — and [FLEE] takes over via the same threshold from the other side.
     */
    val KILL_ENEMY = Goal(
      name = "KillEnemy",
      priority = priority(base = 85f) {
        consider(AGGRESSION.linear())
        consider(HEALTH_PCT.linear())
      },
      availability = Precondition { s -> enemyInSight(s) && !isWounded(s) },
      desiredState = listOf(Preconditions.equalTo(TARGET_DEAD, true)),
    )

    /**
     * Self-preservation. Available only while actually threatened *and* wounded — the same
     * [FLEE_THRESHOLD_PCT] that switches [KILL_ENEMY] off, so those two can never both be available and the
     * creature does not oscillate between charging and bolting.
     *
     * The base is deliberately far above every other goal's, because [KILL_ATTACKER] *is* still available
     * while wounded: being attacked is not something a creature stops noticing because it is hurt. Retaliation
     * therefore remains the fallback if fleeing turns out to be impossible — a cornered animal fights — while
     * an escape route that exists is always preferred. [fleeUrgency]'s floor is what guarantees that ordering
     * for every archetype rather than only for those whose threshold happens to be low.
     */
    val FLEE = Goal(
      name = "Flee",
      priority = priority(base = 200f) { consider(fleeUrgency) },
      availability = Precondition { s -> enemyInSight(s) && isWounded(s) },
      desiredState = listOf(Preconditions.equalTo(SAFE, true)),
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

    val ALL = listOf(EAT_VEGETATION, SLEEP, RETURN_HOME, KILL_ATTACKER, KILL_ENEMY, FLEE, WANDER)

    val BY_NAME = ALL.associateBy { it.name }
  }

  /**
   * How to build each template this domain knows, keyed by the id a profile names it with.
   *
   * Templates need real collaborators now that they carry behaviour as well as a planning contract —
   * [Locomotion] to move and [SkillExecutionService] to attack — so this is a map of *factories* rather
   * than of instances. Keeping it as one map means [ACTION_IDS] and [actionTemplates] cannot drift
   * apart, which a hand-maintained second list of ids inevitably would.
   */
  private val TEMPLATE_FACTORIES:
    Map<String, (Locomotion, SkillExecutionService, List<AttackDefinition>) -> ActionTemplate> = mapOf(
    "wander" to { loc, _, _ -> WanderActionTemplate(loc) },
    "returnHome" to { loc, _, _ -> ReturnHomeActionTemplate(loc) },
    "walkToVegetation" to { loc, _, _ -> WalkToVegetationActionTemplate(loc) },
    "eatVegetation" to { _, _, _ -> EatVegetationActionTemplate() },
    "sleep" to { _, _, _ -> SleepActionTemplate() },
    "approachTarget" to { loc, _, _ -> ApproachTargetActionTemplate(loc) },
    "attack" to { _, skills, attacks -> AttackActionTemplate(attacks, skills) },
    "flee" to { loc, _, _ -> FleeActionTemplate(loc) },
  )

  /** Every action id a profile may name, for fail-fast validation at boot without building anything. */
  val ACTION_IDS: Set<String> get() = TEMPLATE_FACTORIES.keys

  /** Every action template this domain knows, keyed by [ActionTemplate.id] for profile lookups. */
  fun actionTemplates(
    locomotion: Locomotion,
    skills: SkillExecutionService,
    attacks: List<AttackDefinition> = emptyList(),
  ): Map<String, ActionTemplate> =
    TEMPLATE_FACTORIES.mapValues { (_, build) -> build(locomotion, skills, attacks) }

  /** Builds the [ActionResolver] for a profile's declared [actionIds] and known [attacks]. */
  fun resolver(
    actionIds: List<String>,
    locomotion: Locomotion,
    skills: SkillExecutionService,
    attacks: List<AttackDefinition> = emptyList(),
  ): ActionResolver {
    val catalog = actionTemplates(locomotion, skills, attacks)
    return CompositeActionResolver(actionIds.mapNotNull { catalog[it] })
  }
}
