package net.bestia.zone.ai.ecs

import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.bestia.diary.BestiaHappened
import net.bestia.zone.bestia.diary.BestiaNews
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/**
 * Tells the master's diary when an owned bestia is attacked after a quiet while, and when it retreats. A guard in a
 * busy area is hit all day, so a fight is told once, not every blow.
 */
@SpringComponent
class OwnedBestiaWatchSystem(
  private val news: BestiaNews,
  private val now: () -> Long = java.lang.System::currentTimeMillis,
) : System {
  // After the act stage, so it sees the goal the agent has just taken up.
  override val phase = Phase.AI
  override val after = setOf(AiActSystem::class)
  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val reads: ComponentClassSet = setOf(AiAgent::class, OwnedBestia::class, TakenDamage::class)
  override val writes: ComponentClassSet = emptySet()

  private val retreating = HashSet<EntityId>()
  private val lastHitAt = HashMap<EntityId, Long>()

  override fun update(world: World, deltaTime: Float) {
    val time = now()
    val seen = HashSet<EntityId>()

    world.query(AiAgent::class, OwnedBestia::class).each { id ->
      seen += id
      val owned = get<OwnedBestia>()
      if (startedRetreat(id, get<AiAgent>())) tell(owned, BestiaHappened.Kind.RETREATED)
      if (hitAfterQuiet(world, id, time)) tell(owned, BestiaHappened.Kind.ATTACKED)
    }

    retreating.retainAll(seen)
    lastHitAt.keys.retainAll(seen)
  }

  private fun startedRetreat(id: EntityId, agent: AiAgent): Boolean {
    val retreatingNow = agent.currentGoal?.name == RETREAT_GOAL
    val wasRetreating = id in retreating
    if (retreatingNow) retreating.add(id) else retreating.remove(id)

    return retreatingNow && !wasRetreating
  }

  private fun hitAfterQuiet(world: World, id: EntityId, time: Long): Boolean {
    world.get(id, TakenDamage::class)?.mostRecentAttacker(HIT_WINDOW_MS, time) ?: return false
    val last = lastHitAt.put(id, time)

    return last == null || time - last > QUIET_MS
  }

  private fun tell(owned: OwnedBestia, kind: BestiaHappened.Kind) {
    news.tell(BestiaHappened(this, owned.masterId, owned.playerBestiaId, kind))
  }

  companion object {
    private const val RETREAT_GOAL = "Retreat"

    /** A hit this recent is what the sweep, once a second, has not seen yet. */
    private const val HIT_WINDOW_MS = 1_500L

    /** Ten minutes without a hit make the next one a new fight. */
    const val QUIET_MS = 10 * 60 * 1000L
  }
}
