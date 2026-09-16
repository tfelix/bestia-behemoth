package net.bestia.zone.ai.perception

import net.bestia.zone.ai.core.state.CommonKeys
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.ai.ecs.AiThrottle
import net.bestia.zone.ai.ecs.AiThrottleable
import net.bestia.zone.ai.ecs.PlayerControlled
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.AoiLayer
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.battle.damage.TakenDamage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.battle.effects.StatusEffects
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System as EcsSystem
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.environment.time.BestiaDateTime
import net.bestia.zone.geometry.Vec3L
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * First stage of the AI pipeline, and the **only** writer of the domain's observation keys: own position
 * and health, whether a hostile is in sight, who the current target is, whether this bestia has just
 * been attacked, and what time of day the world says it is.
 *
 * That exclusivity is the point. Actions may *simulate* changes to these keys during planning — a walk
 * action has to be able to imagine arriving — but nothing except this system may write one back to live
 * memory, so an agent's beliefs about the world always come from having looked at it. See
 * `StateKey.observed`, which makes the rule mechanical rather than a convention.
 *
 * Runs periodically (~0.5s); this is the perception refresh rate for all NPCs.
 */
@SpringComponent
@Order(10)
class PerceptionSystem(
  private val profileRegistry: AiProfileRegistry,
  private val aoiService: EntityAOIService,
  private val clock: BestiaClock,
  private val throttle: AiThrottle,
) : EcsSystem {

  override val schedule: Schedule = Schedule.EverySeconds(0.5f)

  override val reads: ComponentClassSet = setOf(
    Position::class,
    Health::class,
    Master::class,
    TakenDamage::class,
    StatusEffects::class,
    ActivePlayer::class,
    AiThrottleable::class,
    PlayerControlled::class
  )

  /**
   * `AiAgent` is declared as written, not read: this system mutates the agent's blackboard on every
   * sweep. Getting that wrong is what previously let the scheduler put perception, think and act in one
   * wave — mutually non-conflicting by declaration — so the pipeline ordering they depend on held only
   * because a single wave happens to run in registration order.
   */
  override val writes: ComponentClassSet = setOf(AiAgent::class)

  override fun update(world: World, deltaTime: Float) {
    // Read at most once per sweep and only when there is somebody to tell, rather than hoisted out of the
    // query: the world calendar is anchored to the persisted world row, so asking the clock before the world
    // has finished loading throws — and a zone with no AI in it has no reason to ask at all.
    var time: BestiaDateTime? = null

    // Gathered once per sweep rather than per agent: the throttle asks how far the nearest player is, and
    // there are a handful of players against a hundred and forty creatures each.
    val players = if (throttle.isActive) activePlayerPositions(world) else emptyList()

    world.query(AiAgent::class, Position::class).each { id ->
      val agent = get<AiAgent>()
      val position = get<Position>()

      // Scenery nobody is near perceives less often. Never a den mob, a boss or a player's bestia - see
      // AiThrottle. The gate is inside the loop rather than in the query because `reads`/`writes` decide
      // scheduling waves, and this system already conflicts with every other AI stage on `AiAgent`.
      val factor = throttle.factorFor(world, id, agent, players)
      if (factor > 1) {
        if (world.tickCount < agent.nextPerceiveTick) return@each
        agent.nextPerceiveTick = world.tickCount + factor * PERCEIVE_PERIOD_TICKS + (id % factor)
      }

      val profile = profileRegistry.get(agent.profileId) ?: return@each
      val selfPos = position.toVec3L()
      val memory = agent.memory
      val now = time ?: clock.now().also { time = it }

      memory.set(CommonKeys.POSITION, selfPos)
      memory.set(CommonKeys.HEALTH_PCT, healthPct(world, id))
      memory.set(CommonKeys.IS_NIGHT, now.isNight)
      memory.set(CommonKeys.MINUTE_OF_DAY, now.minuteOfDay)
      memory.set(CommonKeys.DAY_INDEX, now.absoluteDay.toLong())

      // Being off duty is what "has not slept it out yet" means, and clearing the belief here is what lets
      // the sleep goal become unsatisfied again at every dusk — the reason a rested animal still goes to bed
      // when its night comes round. Asked of the agent rather than its profile because a night watchman
      // rests through exactly the hours a diurnal animal is awake.
      if (agent.restingWindow.isRestingAt(now.minuteOfDay, now.isNight)) {
        memory.remove(CommonKeys.RESTED)
      }

      val nearestHostile = nearestHostile(world, id, selfPos, profile.perception.sightRadius)
      val attacker = recentAttacker(world, id, profile.perception.aggroMemoryMs)

      // Retaliation outranks opportunistic aggression: whoever is actually hitting us is the target,
      // even if something else is closer. `TakenDamage` already records this for experience attribution,
      // so retaliation needs no new bookkeeping — it just needed someone to read it, which is why the
      // aggro key existed but was never set by anything.
      val target = attacker ?: nearestHostile

      memory.set(CommonKeys.IS_AGGRO, attacker != null)
      memory.set(CommonKeys.ENEMY_IN_SIGHT, target != null)

      if (target != null) {
        val targetPos = world.get(target, Position::class)?.toVec3L() ?: selfPos
        memory.set(CommonKeys.TARGET_ID, target)
        memory.set(CommonKeys.TARGET_POSITION, targetPos)
        // Something is alive in front of it, so whatever it killed last is not the question any more. A kill
        // goal asks for `TARGET_DEAD` and the planner skips a goal whose desired state already holds, so a
        // belief carried over from the previous kill is an agent that stands and takes the beating.
        memory.remove(CommonKeys.TARGET_DEAD)
      } else {
        memory.remove(CommonKeys.TARGET_ID)
        memory.remove(CommonKeys.TARGET_POSITION)
        memory.remove(CommonKeys.TARGET_ARCHETYPE)
      }

      // Unblocks planning. Until this is set the think stage leaves the agent alone, so nothing is ever
      // planned from a memory that has no observations in it at all.
      agent.hasPerceived = true
    }
  }

  /**
   * Nearest hostile within [sightRadius].
   *
   * Hostility is still "has a `Master` component", i.e. is a player. Real factions are a separate piece
   * of work; the profile already carries a faction name that nothing consults yet.
   */
  private fun nearestHostile(world: World, self: Long, selfPos: Vec3L, sightRadius: Int): Long? {
    // Dynamic only: a mob in a dense wood is inside the sight radius of hundreds of trees and has
    // nothing to think about any of them. Every candidate costs locked component reads below, so
    // filtering here rather than in the loop is what keeps this affordable once statics are resident.
    val sightSize = sightRadius.toLong() * 2

    return aoiService.queryEntitiesInCube(selfPos, sightSize, AoiLayer.DYNAMIC_ONLY)
      .asSequence()
      .filter { it != self }
      .filter { world.has(it, Master::class) }
      .filterNot { isFeigningDeath(world, it) }
      .mapNotNull { candidate ->
        val pos = world.get(candidate, Position::class)?.toVec3L() ?: return@mapNotNull null
        candidate to selfPos.distance(pos)
      }
      .minByOrNull { it.second }
      ?.first
  }

  /**
   * Whoever damaged this entity within [aggroMemoryMs], if they are still alive.
   *
   * The window is the archetype's rather than a constant: it is far shorter than `TakenDamage`'s own
   * five-minute retention, which exists for loot attribution rather than for holding a grudge, and how long
   * a grudge lasts is exactly the difference between a creature that snaps back and one that hunts you down.
   */
  private fun recentAttacker(world: World, self: Long, aggroMemoryMs: Long): Long? =
    world.get(self, TakenDamage::class)
      ?.mostRecentAttacker(aggroMemoryMs)
      ?.takeIf { world.isAlive(it) && !isFeigningDeath(world, it) }

  /**
   * Someone playing dead is not seen and not remembered - dropping them from `recentAttacker` too is
   * what makes the skill an escape rather than a pause, since a grudge outlives being out of sight.
   */
  private fun isFeigningDeath(world: World, entityId: Long): Boolean =
    world.get(entityId, StatusEffects::class)?.hasEffect(StatusEffectId.PLAY_DEAD.id) == true

  private fun healthPct(world: World, entityId: Long): Int {
    val health = world.get(entityId, Health::class) ?: return 100
    if (health.max <= 0) return 0
    return (health.current * 100 / health.max).coerceIn(0, 100)
  }

  /** The same anchor set `SpawnerSystem` and `ChunkStreamSystem` use: only a player who picked a master. */
  private fun activePlayerPositions(world: World): List<Vec3L> {
    val positions = ArrayList<Vec3L>()
    world.query(Position::class, ActivePlayer::class).each {
      positions.add(get<Position>().toVec3L())
    }
    return positions
  }

  private companion object {
    /** Ticks in one ordinary perception sweep, so a throttle factor multiplies a real period. */
    const val PERCEIVE_PERIOD_TICKS = 10L
  }
}
