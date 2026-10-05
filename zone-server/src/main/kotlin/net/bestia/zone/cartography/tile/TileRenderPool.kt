package net.bestia.zone.cartography.tile

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Renders tiles on a few threads of their own, with a bounded queue and a cap on the renders of each master.
 *
 * Every tile request waits on a servlet thread until its render is done. Without bounds, one client asking faster
 * than tiles render would grow the queue without end and hold every servlet thread, and the map would stop for
 * everyone. A full queue or a master at its cap is refused at once instead, and the client asks again later.
 */
class TileRenderPool(
  threads: Int,
  queueCapacity: Int,
  private val rendersPerMaster: Int,
  private val timeoutMillis: Long
) : AutoCloseable {

  /**
   * The pool cannot render this tile now: it is full, or the render took too long.
   *
   * Its own type, so the controller can answer "come back later". A tile refused as 500 is one the client has no
   * reason to ask for again, and a cold zoom level that fails once then stays fog for the whole session.
   */
  class Unavailable(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

  private val workers = ThreadPoolExecutor(
    threads,
    threads,
    0L,
    TimeUnit.MILLISECONDS,
    ArrayBlockingQueue(queueCapacity)
  ) { runnable ->
    Thread(runnable, "map-render").apply { isDaemon = true }
  }

  /** Counted from admission until the request is answered, so a render that timed out still frees its slot. */
  private val rendersInFlight = ConcurrentHashMap<Long, AtomicInteger>()

  internal val queuedRenders: Int get() = workers.queue.size

  fun <T> render(masterId: Long, work: () -> T): T {
    val inFlight = rendersInFlight.computeIfAbsent(masterId) { AtomicInteger() }
    if (inFlight.incrementAndGet() > rendersPerMaster) {
      inFlight.decrementAndGet()
      throw Unavailable("Master $masterId already has $rendersPerMaster renders in flight")
    }

    try {
      return awaitRender(work)
    } finally {
      inFlight.decrementAndGet()
    }
  }

  private fun <T> awaitRender(work: () -> T): T {
    val future = try {
      workers.submit(Callable { work() })
    } catch (e: RejectedExecutionException) {
      throw Unavailable("The render queue is full", e)
    }

    return try {
      future.get(timeoutMillis, TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
      future.cancel(true)
      throw Unavailable("Rendering a tile took longer than $timeoutMillis ms", e)
    } catch (e: ExecutionException) {
      throw e.cause ?: e
    }
  }

  override fun close() {
    workers.shutdownNow()
  }
}
