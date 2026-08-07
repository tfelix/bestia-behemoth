package net.bestia.zone.ai.perception

import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.ecs.AoiLayer
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.battle.damage.TakenDamage
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System as EcsSystem
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * First stage of the AI pipeline, and the **only** writer of the domain's observation keys: own position
 * and health, whether a hostile is in sight, who the current target is, and whether this bestia has just
 * been attacked.
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
) : EcsSystem {

  override val schedule: Schedule = Schedule.EverySeconds(0.5f)

  override val reads: ComponentClassSet =
    setOf(Position::class, Health::class, Master::class, TakenDamage::class)

  /**
   * `AiAgent` is declared as written, not read: this system mutates the agent's blackboard on every
   * sweep. Getting that wrong is what previously let the scheduler put perception, think and act in one
   * wave — mutually non-conflicting by declaration — so the pipeline ordering they depend on held only
   * because a single wave happens to run in registration order.
   */
  override val writes: ComponentClassSet = setOf(AiAgent::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(AiAgent::class, Position::class).each { id ->
      val agent = get<AiAgent>()
      val position = get<Position>()

      val profile = profileRegistry.get(agent.profileId) ?: return@each
      val selfPos = position.toVec3L()
      val memory = agent.memory

      memory.set(BestiaDomain.POSITION, selfPos, Blackboard.PERMANENT)
      memory.set(BestiaDomain.HEALTH_PCT, healthPct(world, id), Blackboard.PERMANENT)

      val nearestHostile = nearestHostile(world, id, selfPos, profile.perception.sightRadius)
      val attacker = recentAttacker(world, id)

      // Retaliation outranks opportunistic aggression: whoever is actually hitting us is the target,
      // even if something else is closer. `TakenDamage` already records this for experience attribution,
      // so retaliation needs no new bookkeeping — it just needed someone to read it, which is why the
      // aggro key existed but was never set by anything.
      val target = attacker ?: nearestHostile

      memory.set(BestiaDomain.IS_AGGRO, attacker != null)
      memory.set(BestiaDomain.ENEMY_IN_SIGHT, target != null)

      if (target != null) {
        val targetPos = world.get(target, Position::class)?.toVec3L() ?: selfPos
        memory.set(BestiaDomain.TARGET_ID, target)
        memory.set(BestiaDomain.TARGET_POSITION, targetPos)
        memory.set(BestiaDomain.THREAT_POSITION, targetPos)
        // Being in sight of something hostile is exactly what "no longer safe" means, and clearing the
        // belief here is what lets the flee goal become unsatisfied again after a previous escape.
        memory.remove(BestiaDomain.SAFE)
      } else {
        memory.remove(BestiaDomain.TARGET_ID)
        memory.remove(BestiaDomain.TARGET_POSITION)
        memory.remove(BestiaDomain.THREAT_POSITION)
        memory.remove(BestiaDomain.TARGET_ARCHETYPE)
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
      .mapNotNull { candidate ->
        val pos = world.get(candidate, Position::class)?.toVec3L() ?: return@mapNotNull null
        candidate to selfPos.distance(pos)
      }
      .minByOrNull { it.second }
      ?.first
  }

  /** Whoever damaged this entity within the aggro window, if they are still alive. */
  private fun recentAttacker(world: World, self: Long): Long? =
    world.get(self, TakenDamage::class)
      ?.mostRecentAttacker(AGGRO_MEMORY_MS)
      ?.takeIf { world.isAlive(it) }

  private fun healthPct(world: World, entityId: Long): Int {
    val health = world.get(entityId, Health::class) ?: return 100
    if (health.max <= 0) return 0
    return (health.current * 100 / health.max).coerceIn(0, 100)
  }

  companion object {
    /**
     * How long after being hit a bestia keeps hunting its attacker. Much shorter than `TakenDamage`'s own
     * five-minute retention, which exists for loot attribution rather than for holding a grudge.
     */
    private const val AGGRO_MEMORY_MS = 10_000L
  }
}
