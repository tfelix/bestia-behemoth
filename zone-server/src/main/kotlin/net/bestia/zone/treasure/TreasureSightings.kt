package net.bestia.zone.treasure

import net.bestia.worldgen.treasure.TreasureCache
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The caches each master sensed lately, which Treasure Map charts from. In memory and tick thread only: a
 * restart forgets them, and a Prospector senses again.
 */
@Service
class TreasureSightings(
  private val clock: Clock = Clock.systemUTC(),
) {

  private val byMaster = HashMap<Long, List<Sighting>>()

  fun record(masterId: Long, caches: List<TreasureCache>) {
    val now = clock.instant()
    val kept = recentOf(masterId, now).filter { old -> caches.none { it.id == old.cache.id } }
    byMaster[masterId] = kept + caches.map { Sighting(it, now) }
  }

  /** What the master sensed within the last [WINDOW], nearest to the point first. */
  fun recentNear(masterId: Long, x: Double, y: Double): List<TreasureCache> {
    return recentOf(masterId, clock.instant())
      .map { it.cache }
      .sortedBy { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) }
  }

  private fun recentOf(masterId: Long, now: Instant): List<Sighting> {
    return byMaster[masterId].orEmpty().filter { Duration.between(it.at, now) <= WINDOW }
  }

  private class Sighting(val cache: TreasureCache, val at: Instant)

  private companion object {
    val WINDOW: Duration = Duration.ofMinutes(10)
  }
}
