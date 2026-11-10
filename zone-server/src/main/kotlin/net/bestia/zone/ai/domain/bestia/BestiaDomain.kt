package net.bestia.zone.ai.domain.bestia

import net.bestia.zone.battle.attack.BasicAttackResolver
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
import net.bestia.zone.ai.domain.bestia.action.ClaimStationActionTemplate
import net.bestia.zone.ai.domain.bestia.action.CollectPropActionTemplate
import net.bestia.zone.ai.domain.bestia.action.EatVegetationActionTemplate
import net.bestia.zone.ai.domain.bestia.action.FellTreeActionTemplate
import net.bestia.zone.ai.domain.bestia.action.HaulToChestActionTemplate
import net.bestia.zone.ai.domain.bestia.action.RetreatHomeActionTemplate
import net.bestia.zone.ai.domain.bestia.action.UseFromBagActionTemplate
import net.bestia.zone.ai.domain.bestia.action.ReturnHomeActionTemplate
import net.bestia.zone.ai.domain.bestia.action.ReturnToMiddleActionTemplate
import net.bestia.zone.ai.domain.bestia.action.SleepActionTemplate
import net.bestia.zone.ai.domain.bestia.action.TakeFoodFromChestActionTemplate
import net.bestia.zone.ai.domain.bestia.action.WalkToVegetationActionTemplate
import net.bestia.zone.ai.domain.bestia.action.WanderActionTemplate
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.battle.CombatTargetPromotion
import net.bestia.zone.battle.attack.AttackExecutionService
import net.bestia.zone.casting.SkillExecutionService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.care.BagCare
import net.bestia.zone.item.container.ItemHandover

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
  const val DEFAULT_ATTACK_RANGE = 1L
  const val DEFAULT_RESTLESS_THRESHOLD = 60

  /**
   * Tiredness at or below which a creature counts as rested.
   *
   * Shared between [Goals.SLEEP]'s desired state and the sleep behaviour's own "am I done" test on purpose:
   * two copies of this number would let a creature stop sleeping while its goal still considered it tired,
   * or the reverse, and both read as a mob twitching in and out of bed.
   */
  const val RESTED_TIREDNESS = 20

  /** Care lines an owned bestia looks after itself at, until its orders can change them. */
  const val DEFAULT_EAT_BELOW_STAMINA_PCT = 40
  const val DEFAULT_HEAL_BELOW_HEALTH_PCT = 50

  /** A retreat ends this close to home: near enough to be out of the fight, far enough not to be a single tile. */
  const val RETREAT_ARRIVAL_RADIUS = 3L

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
  val TARGET_LEVEL_LEAD = CommonKeys.TARGET_LEVEL_LEAD

  // ------------------------------------------------------------------ this domain

  /** How close it gets before it attacks: the reach of its longest default attack. */
  val ATTACK_RANGE = StateKey<Long>("attackRange", retain = Blackboard.PERMANENT)

  /** The reach of a shot its held weapon can fire now; absent without a ranged weapon or ammo for it. */
  val SHOT_REACH = StateKey<Long>("shotReach", observed = true)

  /** 0..100 temperament knob; scales how strongly the kill goals are wanted. */
  val AGGRESSION = StateKey<Int>("aggression", retain = Blackboard.PERMANENT)

  /** Stamina left, 0..100. Only an owned bestia acts on it. */
  val STAMINA_PCT = StateKey<Int>("staminaPct", observed = true, retain = Blackboard.PERMANENT)

  /** Whether its own bag holds something it may eat for stamina, or put on for health. See [BagCare]. */
  val FOOD_IN_BAG = StateKey<Boolean>("foodInBag", observed = true)
  val HEAL_IN_BAG = StateKey<Boolean>("healInBag", observed = true)

  /** An owned bestia's care and courage lines, from its orders. Wild mobs have none. */
  val EAT_BELOW_STAMINA_PCT = StateKey<Int>("eatBelowStaminaPct", retain = Blackboard.PERMANENT)
  val HEAL_BELOW_HEALTH_PCT = StateKey<Int>("healBelowHealthPct", retain = Blackboard.PERMANENT)
  val RETREAT_BELOW_HEALTH_PCT = StateKey<Int>("retreatBelowHealthPct", retain = Blackboard.PERMANENT)
  val FLEES_ON_SIGHT = StateKey<Boolean>("fleesOnSight", retain = Blackboard.PERMANENT)
  val TAKES_ON_STRONGER_FOES = StateKey<Boolean>("takesOnStrongerFoes", retain = Blackboard.PERMANENT)

  /** A foe this many levels above a bestia is stronger, and its courage decides whether to start that fight. */
  const val STRONGER_FOE_LEVEL_LEAD = 5

  /** How full its bag is, 0..100 of what it can carry. */
  val BAG_LOAD_PCT = StateKey<Int>("bagLoadPct", observed = true)

  /** A gatherer stops gathering at this load, so it never has to leave a find on the ground for want of room. */
  const val HAUL_AT_LOAD_PCT = 80

  /** The props a gatherer last saw in its area, nearest first. See `GatherSense`. */
  val GATHERABLES = StateKey<List<Gatherable>>("gatherables")

  /** Props a gatherer failed to take, by prop id. It looks past them for a while. */
  val GIVEN_UP = StateKey<Set<Long>>("givenUpProps", retain = 300f)

  /** Gathered something a moment ago: [Goals.GATHER] is met by one prop, and wanted again once this lapses. */
  val GATHERED = StateKey<Boolean>("gathered", retain = 1f)

  /** The chest a gatherer fills; absent when its master has none near the area. See `JobChestSense`. */
  val JOB_CHEST = StateKey<JobChest>("jobChest", observed = true)

  /** Whether the job chest has a free slot, or takes some of what the bag holds. */
  val CHEST_HAS_ROOM = StateKey<Boolean>("chestHasRoom", observed = true)

  /** Whether the job chest holds food the bestia may eat. */
  val FOOD_IN_CHEST = StateKey<Boolean>("foodInChest", observed = true)

  /** Its station waits because the next product does not fit in its bag. Set by the station while it waits. */
  val NO_ROOM_FOR_PRODUCT = StateKey<Boolean>("noRoomForProduct", observed = true, retain = 10f)

  /** Whether the bag holds goods for the job chest that still fit in it. */
  val GOODS_TO_HAUL = StateKey<Boolean>("goodsToHaul", observed = true)

  /** Where a bestia under GATHER orders works; absent under any other job. */
  val GATHER_AREA = StateKey<WorkArea>("gatherArea", retain = Blackboard.PERMANENT)

  /** Where a bestia under CRAFT orders looks for stations to work; absent under any other job. */
  val CRAFT_AREA = StateKey<WorkArea>("craftArea", retain = Blackboard.PERMANENT)

  /** Where a bestia under GUARD orders holds off intruders; absent under any other job. */
  val GUARD_AREA = StateKey<WorkArea>("guardArea", retain = Blackboard.PERMANENT)

  /** A guard has walked back to the middle of its area since its last fight. Perception clears it on every target. */
  val BACK_IN_MIDDLE = StateKey<Boolean>("backInMiddle", retain = Blackboard.PERMANENT)

  /** Its master's stations in its craft area that wait for a free crafter, nearest first. See `StationSense`. */
  val OPEN_STATIONS = StateKey<List<OpenStation>>("openStations")

  /** Whether it works a station now. */
  val AT_WORK = StateKey<Boolean>("atWork")

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
   * Peckish in about three real minutes and sleepy in seven, against the world clock. Tiredness runs
   * backwards while asleep, and twenty times as fast, so a full night is slept off well before dawn; an
   * interrupted night therefore means something, because the recovery is continuous rather than a jump when
   * the sleeping finishes.
   *
   * Restlessness is against the wall instead, because it decides how long a creature stands about between
   * roams and that is something a player watches: at 15 a second it crosses [DEFAULT_RESTLESS_THRESHOLD]
   * four seconds after a roam spent it, which is the pause `Wander` already leaves between the legs of one.
   */
  val DRIVES = listOf(
    Drive.perGameHour(HUNGER, 660f),
    Drive.perGameHour(TIREDNESS, 300f, whileSleeping = -6_000f),
    Drive.perRealSecond(RESTLESSNESS, 15f),
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

  private fun eatLineOf(state: WorldState): Int = state.get(EAT_BELOW_STAMINA_PCT) ?: DEFAULT_EAT_BELOW_STAMINA_PCT

  private fun bagLoadOf(state: WorldState): Int = state.get(BAG_LOAD_PCT) ?: 0

  private fun healLineOf(state: WorldState): Int = state.get(HEAL_BELOW_HEALTH_PCT) ?: DEFAULT_HEAL_BELOW_HEALTH_PCT

  /**
   * Whether courage says to get out: hurt below its retreat line, or a timid one seeing an enemy at all.
   * Shared by [Goals.RETREAT] and its action, so the walk home is never planned for any other reason.
   */
  fun retreatWarranted(state: WorldState): Boolean {
    val line = state.get(RETREAT_BELOW_HEALTH_PCT)
    val hurt = line != null && healthPctOf(state) < line
    val frightened = state.get(FLEES_ON_SIGHT) == true && enemyInSight(state)
    return hurt || frightened
  }

  /** Wild mobs have no courage and take on anything. */
  private fun courageHoldsBack(state: WorldState): Boolean {
    val stronger = (state.get(TARGET_LEVEL_LEAD) ?: 0) >= STRONGER_FOE_LEVEL_LEAD

    return stronger && state.get(TAKES_ON_STRONGER_FOES) == false
  }

  fun atRetreat(state: WorldState): Boolean =
    (homeDistanceOrNull(state) ?: Long.MAX_VALUE) <= RETREAT_ARRIVAL_RADIUS

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
     * A wild mob never runs away, so for it this is the *whole* of what being attacked provokes, and its base
     * outranks every goal a wild mob has on purpose: a creature that is being hit fights back rather than
     * wandering off to graze or lying down to sleep mid-fight. Only an owned bestia's [RETREAT] outranks it. It stays available however badly hurt — there is no
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
     * [KILL_ATTACKER] answers it, unscaled. That is also why an owned bestia's courage may hold it back from a
     * stronger foe here and still lets it fight back.
     */
    val KILL_ENEMY = Goal(
      name = "KillEnemy",
      priority = priority(base = 85f) {
        consider(AGGRESSION.linear())
        consider(HEALTH_PCT.linear())
      },
      availability = Precondition { s -> enemyInSight(s) && !courageHoldsBack(s) },
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

    /**
     * An owned bestia putting on a bandage or eating something healing from its own bag. Above
     * [KILL_ATTACKER] so it does so mid-fight, which is when it matters.
     */
    val HEAL_FROM_BAG = Goal(
      name = "HealFromBag",
      priority = priority(base = 96f),
      availability = Precondition { s -> healthPctOf(s) < healLineOf(s) && s.get(HEAL_IN_BAG) == true },
      desiredState = listOf(Precondition { s -> healthPctOf(s) >= healLineOf(s) }),
    )

    /** An owned bestia eating from its own bag once its stamina runs low. Not mid-fight: food can wait. */
    val EAT_FROM_BAG = Goal(
      name = "EatFromBag",
      priority = priority(base = 88f),
      availability = Precondition { s ->
        (s.get(STAMINA_PCT) ?: 100) < eatLineOf(s) && s.get(FOOD_IN_BAG) == true
      },
      desiredState = listOf(Precondition { s -> (s.get(STAMINA_PCT) ?: 100) >= eatLineOf(s) }),
    )

    /** Out of food with stamina low: fetch some from the job chest. Just under [EAT_FROM_BAG], which then eats it. */
    val RESTOCK_FOOD = Goal(
      name = "RestockFood",
      priority = priority(base = 87f),
      availability = Precondition { s ->
        (s.get(STAMINA_PCT) ?: 100) < eatLineOf(s) && s.get(FOOD_IN_BAG) != true && s.get(FOOD_IN_CHEST) == true
      },
      desiredState = listOf(Preconditions.equalTo(FOOD_IN_BAG, true)),
    )

    /**
     * An owned bestia walking home out of a fight its courage says it is losing. Above every other goal,
     * [KILL_ATTACKER] included, or it would never leave a fight. Satisfied once home: if the attacker follows
     * it there, [KILL_ATTACKER] takes over again, and while it stays hurt it does not stray far.
     */
    val RETREAT = Goal(
      name = "Retreat",
      priority = priority(base = 97f),
      availability = Precondition { s -> retreatWarranted(s) && !atRetreat(s) },
      desiredState = listOf(Precondition { s -> atRetreat(s) }),
    )

    /**
     * A gatherer at work, one prop per plan. Under [RETURN_HOME], whose radius is the area's own, so it never
     * works its way out of the area; over [WANDER], so idling is what it does once nothing is left.
     */
    val GATHER = Goal(
      name = "Gather",
      priority = priority(base = 60f),
      // Without room in its chest a gatherer stops, rather than gather what it could never put away.
      availability = Precondition { s ->
        !s.get(GATHERABLES).isNullOrEmpty() && bagLoadOf(s) < HAUL_AT_LOAD_PCT && s.get(CHEST_HAS_ROOM) == true
      },
      desiredState = listOf(Preconditions.equalTo(GATHERED, true)),
    )

    /**
     * A gatherer or crafter carrying its goods to its chest, once its bag is nearly full, the area is picked clean or
     * its station has no room for the next product. Over [RETURN_HOME], so a chest outside the area is reached rather
     * than turned back from.
     */
    val HAUL_TO_CHEST = Goal(
      name = "HaulToChest",
      priority = priority(base = 75f),
      availability = Precondition { s ->
        val pickedClean = s.get(GATHER_AREA) != null && s.get(GATHERABLES).isNullOrEmpty()
        val bagFull = bagLoadOf(s) >= HAUL_AT_LOAD_PCT || s.get(NO_ROOM_FOR_PRODUCT) == true
        s.get(GOODS_TO_HAUL) == true && (bagFull || pickedClean)
      },
      desiredState = listOf(Preconditions.equalTo(GOODS_TO_HAUL, false)),
    )

    /**
     * A free crafter taking a station that waits for one. The station is then its home, so [RETURN_HOME] keeps it
     * there; over [WANDER], so idling is what it does once no station waits.
     */
    val CRAFT = Goal(
      name = "Craft",
      priority = priority(base = 60f),
      availability = Precondition { s -> s.get(AT_WORK) != true && !s.get(OPEN_STATIONS).isNullOrEmpty() },
      desiredState = listOf(Preconditions.equalTo(AT_WORK, true)),
    )

    /** A guard walking back to the middle of its area once a fight is over. Over [WANDER], under the kill goals. */
    val RETURN_TO_MIDDLE = Goal(
      name = "ReturnToMiddle",
      priority = priority(base = 70f),
      availability = Precondition { s -> s.get(GUARD_AREA) != null && !enemyInSight(s) },
      desiredState = listOf(Preconditions.equalTo(BACK_IN_MIDDLE, true)),
    )

    val ALL = listOf(
      EAT_VEGETATION, SLEEP, RETURN_HOME, KILL_ATTACKER, KILL_ENEMY, WANDER, HEAL_FROM_BAG, EAT_FROM_BAG, RETREAT,
      GATHER, HAUL_TO_CHEST, RESTOCK_FOOD, CRAFT, RETURN_TO_MIDDLE,
    )

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
    val basicAttacks: BasicAttackResolver,
    val care: BagCare,
    val promotion: CombatTargetPromotion,
    val aoi: EntityAOIService,
    val handover: ItemHandover,
    val stations: StationClaims,
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
    "returnToMiddle" to { c -> ReturnToMiddleActionTemplate(c.locomotion) },
    "walkToVegetation" to { c -> WalkToVegetationActionTemplate(c.locomotion) },
    "eatVegetation" to { _ -> EatVegetationActionTemplate() },
    "sleep" to { _ -> SleepActionTemplate() },
    "approachTarget" to { c -> ApproachTargetActionTemplate(c.locomotion) },
    "attack" to { c -> AttackActionTemplate(c.attacks, c.skills, c.attackExecution, c.basicAttacks) },
    "healFromBag" to { c -> UseFromBagActionTemplate.heal(c.care) },
    "eatFromBag" to { c -> UseFromBagActionTemplate.eat(c.care) },
    "retreatHome" to { c -> RetreatHomeActionTemplate(c.locomotion) },
    "collectProp" to { c -> CollectPropActionTemplate(c.locomotion) },
    "fellTree" to { c -> FellTreeActionTemplate(c.locomotion, c.attackExecution, c.promotion, c.aoi, c.attacks) },
    "haulToChest" to { c -> HaulToChestActionTemplate(c.locomotion, c.care, c.handover, c.aoi) },
    "takeFoodFromChest" to { c -> TakeFoodFromChestActionTemplate(c.locomotion, c.care, c.handover, c.aoi) },
    "claimStation" to { c -> ClaimStationActionTemplate(c.locomotion, c.stations) },
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
