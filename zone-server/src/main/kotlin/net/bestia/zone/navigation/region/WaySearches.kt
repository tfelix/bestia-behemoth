package net.bestia.zone.navigation.region

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import net.bestia.zone.geometry.Vec3L
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Searches [RegionGrid] off the tick thread: a way of ten kilometres surveys dozens of blocks and expands tens
 * of thousands of cells, which is a second's work the first time and tens of milliseconds after, against a
 * tick of fifty.
 *
 * Asked again each tick until the answer is in, [way] says [RegionGrid.Way.Pending] meanwhile, and the same
 * answer every time after: a plan that needs two ways waits for both, and must not lose the first while it
 * does. A search nobody asks about for [FORGET_AFTER_NANOS] - its traveller has its plan, stopped, or chose
 * another way - is dropped, answered or not.
 *
 * With no pool it searches at once, on the caller, which is what tests want.
 */
@Component
class WaySearches internal constructor(private val grid: RegionGrid, private val pool: Executor?) {

  @Autowired
  constructor(grid: RegionGrid) : this(grid, newPool())

  /**
   * Which search an ask is about. By the cell the traveller starts from rather than where exactly it stands, so
   * one still walking the last of an old path while it waits is not asked about as a new search every tick.
   */
  private data class Key(val fromCell: Long, val to: Vec3L, val avoid: List<Pair<Vec3L, Vec3L>>)

  private class Search(val result: CompletableFuture<RegionGrid.Way>, var lastAskedAt: Long)

  /** Tick thread only. */
  private val searches = HashMap<Key, Search>()

  /** The way from [from] to [to] keeping clear of [avoid], [RegionGrid.Way.Pending] until it is searched. */
  fun way(from: Vec3L, to: Vec3L, avoid: List<Pair<Vec3L, Vec3L>> = emptyList()): RegionGrid.Way {
    val pool = pool ?: return grid.wayBetween(from, to, avoid)

    val now = System.nanoTime()
    forgetUnaskedSince(now - FORGET_AFTER_NANOS)

    val key = Key(cellKey(from), to, avoid)
    val search = searches.getOrPut(key) {
      Search(CompletableFuture.supplyAsync({ grid.wayBetween(from, to, avoid) }, pool), now)
    }
    search.lastAskedAt = now
    if (!search.result.isDone) return RegionGrid.Way.Pending

    return runCatching { search.result.join() }.getOrElse { failure ->
      LOG.error(failure) { "Searching the way from $from to $to failed; walking it straight" }
      RegionGrid.Way.Unknown
    }
  }

  private fun forgetUnaskedSince(cutoff: Long) {
    val forgotten = searches.values.filter { it.lastAskedAt < cutoff }
    if (forgotten.isEmpty()) return

    forgotten.forEach { it.result.cancel(false) }
    searches.values.removeAll(forgotten.toSet())
  }

  @PreDestroy
  fun shutdown() {
    val service = pool as? ExecutorService ?: return
    service.shutdownNow()
    service.awaitTermination(5, TimeUnit.SECONDS)
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    val FORGET_AFTER_NANOS = TimeUnit.SECONDS.toNanos(10)

    const val CELL = 32L

    fun cellKey(position: Vec3L): Long {
      return (Math.floorDiv(position.x, CELL) shl 32) or (Math.floorDiv(position.y, CELL) and 0xFFFFFFFFL)
    }

    /**
     * One thread. Searches are rare - one per journey and stretch - and a second thread would mostly survey the
     * same blocks as the first; what matters is that none of it is on the tick.
     */
    fun newPool(): ExecutorService {
      return Executors.newSingleThreadExecutor { r -> Thread(r, "zone-way-search").apply { isDaemon = true } }
    }
  }
}
