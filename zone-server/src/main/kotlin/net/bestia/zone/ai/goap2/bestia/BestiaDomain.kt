package net.bestia.zone.ai.goap2.bestia

import net.bestia.zone.ai.goap2.action.ActionTemplate
import net.bestia.zone.ai.goap2.action.CompositeActionResolver
import net.bestia.zone.ai.goap2.bestia.action.ApproachTargetActionTemplate
import net.bestia.zone.ai.goap2.bestia.action.AttackActionTemplate
import net.bestia.zone.ai.goap2.bestia.action.EatVegetationActionTemplate
import net.bestia.zone.ai.goap2.bestia.action.ReturnHomeActionTemplate
import net.bestia.zone.ai.goap2.bestia.action.SleepActionTemplate
import net.bestia.zone.ai.goap2.bestia.action.WalkToVegetationActionTemplate
import net.bestia.zone.ai.goap2.bestia.action.WanderActionTemplate
import net.bestia.zone.ai.goap2.action.Action
import net.bestia.zone.ai.goap2.goal.Goal
import net.bestia.zone.ai.goap2.goal.inverseLinear
import net.bestia.zone.ai.goap2.goal.linear
import net.bestia.zone.ai.goap2.goal.priority
import net.bestia.zone.ai.goap2.precondition.Precondition
import net.bestia.zone.ai.goap2.precondition.Preconditions
import net.bestia.zone.ai.goap2.state.MemoryScope
import net.bestia.zone.ai.goap2.state.StateKey
import net.bestia.zone.ai.goap2.state.WorldState
import net.bestia.zone.geometry.Vec3L

/**
 * The concrete GOAP domain for bestia mobs: state keys, the NON_AGGRO/AGGRO goal sets, and the
 * catalog of [ActionTemplate]s that ground them. Built on top of the domain-agnostic goap2 core the
 * same way `net.bestia.zone.ai.goap.MarketDomain` demonstrates it for the villager scenario, but
 * composed from small, independently testable templates (see [ActionTemplate]) rather than one big
 * resolver closure.
 */
object BestiaDomain {

  /** Grid tiles counted as "arrived" — [net.bestia.zone.geometry.Vec3L.distance] is exact tile distance. */
  const val ARRIVAL_RADIUS = 1L
  const val DEFAULT_WANDER_RADIUS = 5L
  const val DEFAULT_MELEE_RANGE = 1L

  val POSITION = StateKey<Vec3L>("position")

  /** Spawn tile, written once (permanently) when a profile is attached — see `BestiaAgentFactory`. */
  val HOME_POSITION = StateKey<Vec3L>("homePosition")
  val WANDER_RADIUS = StateKey<Long>("wanderRadius")
  val MELEE_RANGE = StateKey<Long>("meleeRange")

  val HUNGER = StateKey<Int>("hunger")
  val HUNGER_THRESHOLD = StateKey<Int>("hungerThreshold")
  val TIREDNESS = StateKey<Int>("tiredness")
  val TIREDNESS_THRESHOLD = StateKey<Int>("tirednessThreshold")

  /** Shared pack-wide: one bestia's foraging discovery becomes every packmate's knowledge. */
  val KNOWN_VEGETATION = StateKey<List<VegetationMemory>>("knownVegetation", MemoryScope.TEAM)

  /** Shared world-wide: "fire hurts golems" is knowledge the whole species can learn once. */
  val ATTACK_EFFECTIVENESS =
    StateKey<Map<EffectivenessKey, Double>>("attackEffectiveness", MemoryScope.WORLD)

  val IS_AGGRO = StateKey<Boolean>("isAggro")
  val TARGET_ID = StateKey<String>("targetId")
  val TARGET_ARCHETYPE = StateKey<String>("targetArchetype")
  val TARGET_POSITION = StateKey<Vec3L>("targetPosition")
  val TARGET_DEAD = StateKey<Boolean>("targetDead")

  internal fun distanceOrMax(a: Vec3L?, b: Vec3L?): Long =
    if (a == null || b == null) Long.MAX_VALUE else a.distance(b)

  private fun homeDistance(state: WorldState): Long = distanceOrMax(state.get(POSITION), state.get(HOME_POSITION))
  private fun wanderRadiusOf(state: WorldState): Long = state.get(WANDER_RADIUS) ?: DEFAULT_WANDER_RADIUS

  object Goals {

    val EAT_VEGETATION = Goal(
      name = "EatVegetation",
      priority = priority(base = 80f) { consider(HUNGER.inverseLinear()) },
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
     * Unlike [EAT_VEGETATION]/[SLEEP] this isn't gated on a decaying resource but on a live distance
     * check over two positional keys at once, which is why its precondition is a raw [Precondition]
     * lambda rather than one of the single-key [Preconditions] helpers.
     */
    val RETURN_HOME = Goal(
      name = "ReturnHome",
      priority = priority(base = 70f),
      availability = Precondition { s -> homeDistance(s) > wanderRadiusOf(s) },
      desiredState = listOf(Precondition { s -> homeDistance(s) <= wanderRadiusOf(s) }),
    )

    /** Gated purely on [IS_AGGRO], which perception (outside goap2) flips true when the bestia is attacked. */
    val KILL_ATTACKER = Goal(
      name = "KillAttacker",
      priority = priority(base = 95f),
      availability = Preconditions.equalTo(IS_AGGRO, true),
      desiredState = listOf(Preconditions.equalTo(TARGET_DEAD, true)),
    )

    val NON_AGGRO = listOf(EAT_VEGETATION, SLEEP, RETURN_HOME)
    val AGGRO = listOf(KILL_ATTACKER)
    val ALL = NON_AGGRO + AGGRO
  }

  /**
   * The idle default for when no goal in [Goals.ALL] is available/unsatisfied — deliberately *not*
   * a [Goal] itself. A "keep wandering" goal's desired state would either hold trivially before any
   * step is taken (so it would never be selected — [net.bestia.zone.ai.goap2.planner.Planner] only
   * picks goals that aren't already satisfied) or hold permanently after one step once written back
   * to memory (so it would never run again). A reflexive single-step fallback sidesteps both: the
   * caller asks the planner first and only falls back to this when it comes back empty.
   *
   * Correspondingly, a profile's `actions` list (and therefore [resolver]'s input) should never
   * include `"wander"`: since [WanderActionTemplate] is unconditionally groundable and always lands
   * back within the wander radius by construction, mixing it into goal-directed search would let it
   * masquerade as a free way to satisfy [Goals.RETURN_HOME] (or anything else keyed on position)
   * purely by luck of the random target, short-circuiting the goal it was supposed to compete with.
   */
  fun fallbackWander(state: WorldState, wander: ActionTemplate = WanderActionTemplate()): Action? =
    wander.ground(state).firstOrNull()

  /**
   * Every action template this domain knows, keyed by [ActionTemplate.id] for profile lookups. Kept
   * here (rather than dropping [WanderActionTemplate] entirely) so [fallbackWander] and tests can
   * still look it up by id — but see the warning on [fallbackWander] about never feeding `"wander"`
   * into [resolver].
   */
  fun actionTemplates(attacks: List<AttackDefinition> = emptyList()): Map<String, ActionTemplate> = listOf(
    WanderActionTemplate(),
    ReturnHomeActionTemplate(),
    WalkToVegetationActionTemplate(),
    EatVegetationActionTemplate(),
    SleepActionTemplate(),
    ApproachTargetActionTemplate(),
    AttackActionTemplate(attacks),
  ).associateBy { it.id }

  /** Builds the [CompositeActionResolver] for a profile's declared [actionIds] and known [attacks]. */
  fun resolver(actionIds: List<String>, attacks: List<AttackDefinition> = emptyList()): CompositeActionResolver {
    val catalog = actionTemplates(attacks)
    return CompositeActionResolver(actionIds.mapNotNull { catalog[it] })
  }
}
