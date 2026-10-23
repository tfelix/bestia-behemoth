package net.bestia.zone.travel.knowledge

import net.bestia.zone.travel.TravelConfig
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Which macro edges each [Knower] believes are blocked, from what its travellers have seen.
 *
 * The planner asks this instead of the true state of the world: a bridge that fell where nobody of a master's
 * saw it is still believed standing, and is walked to before the detour. Only blockages are kept, since an
 * edge nobody saw is open as generated; one seen standing again is forgotten. Every belief lapses, by default
 * after [TravelConfig.knowledgeTtl].
 *
 * Tick thread only.
 */
@Service
class RouteKnowledge(
  private val config: TravelConfig,
  private val clock: Clock = Clock.systemUTC(),
) {

  private val memories = HashMap<Knower, Memory>()
  private val learned = ArrayList<(Knower, Int, Instant?) -> Unit>()

  /** Called when [observe] changes or renews a belief: the edge, and when the blockage lapses, null for open. */
  fun onLearned(listener: (knower: Knower, edge: Int, lapsesAt: Instant?) -> Unit) {
    learned.add(listener)
  }

  fun believesBlocked(knower: Knower, edge: Int): Boolean {
    val lapsesAt = memories[knower]?.blockedUntil?.get(edge) ?: return false
    return !isLapsed(lapsesAt)
  }

  /**
   * Bumped whenever what [knower] believes changes. A route planned at an older revision may lead through
   * something it now knows is blocked.
   */
  fun revisionOf(knower: Knower): Long {
    return memories[knower]?.revision ?: 0L
  }

  /**
   * Records that [knower] saw [edge] blocked or usable. A blockage is believed for [lasting].
   *
   * @return whether this changed what it believes
   */
  fun observe(knower: Knower, edge: Int, blocked: Boolean, lasting: Duration = config.knowledgeTtl): Boolean {
    val believedBlocked = believesBlocked(knower, edge)

    if (blocked) {
      val lapsesAt = clock.instant().plus(lasting)
      memoryOf(knower).blockedUntil[edge] = lapsesAt
      learned.forEach { it(knower, edge, lapsesAt) }
    } else if (memories[knower]?.blockedUntil?.remove(edge) != null) {
      learned.forEach { it(knower, edge, null) }
    }

    val changed = believedBlocked != blocked
    if (changed) memoryOf(knower).revision++
    return changed
  }

  /** Puts back a belief read from storage, without telling [onLearned] about it. */
  fun restore(knower: Knower, edge: Int, lapsesAt: Instant) {
    if (!isLapsed(lapsesAt)) memoryOf(knower).blockedUntil[edge] = lapsesAt
  }

  fun forget(knower: Knower) {
    memories.remove(knower)
  }

  fun knowers(): Set<Knower> {
    return memories.keys
  }

  fun isLapsed(lapsesAt: Instant): Boolean {
    return !lapsesAt.isAfter(clock.instant())
  }

  private fun memoryOf(knower: Knower): Memory {
    return memories.getOrPut(knower) { Memory() }
  }

  private class Memory {
    val blockedUntil = HashMap<Int, Instant>()
    var revision = 0L
  }
}
