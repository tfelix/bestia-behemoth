package net.bestia.worldgen.core

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * Deterministic work splitting for the loops inside a stage.
 *
 * ### The rule this exists to enforce
 *
 * **Only loops whose every output is a pure function of read-only inputs may be split.** Not a style
 * preference - it is the property that keeps the pipeline correct. If the split reached the result, a
 * twelve-core machine and a four-core machine would generate different worlds from the same seed, and
 * everything downstream rests on them not doing that: the chunk cache key, a client generating base
 * terrain that has to match the server's, and the plan to distribute chunk generation across machines at
 * all. `GenRng`'s own documentation makes the same argument for randomness; this is the same argument for
 * arithmetic.
 *
 * So there is deliberately **no reduce, no accumulator, and no shared output** in this API. A loop that
 * needs one - `Grid.mean`, `normaliseLandFraction`, the invariant counters - stays on one thread, because
 * the moment a sum is split into per-thread partials its rounding depends on how many partials there were.
 * [rows] hands out disjoint bands of output rows and [map] hands out disjoint slots of a result array;
 * in both cases the band count is invisible to what comes out.
 *
 * ### Why threads and not coroutines
 *
 * `worldgen/build.gradle` allows the Kotlin stdlib and the JDK and nothing else, because this module is
 * linked into the zone-server and possibly the client. `java.util.concurrent` is in bounds and
 * `kotlinx.coroutines` is not - and for fixed CPU-bound grid work a pool sized to the core count is what
 * is wanted anyway. [net.bestia.worldgen.core.ChunkSeamCheck] set the precedent.
 *
 * ### Switches
 *
 * `-Dworldgen.parallel=false` forces everything serial, and `-Dworldgen.threads=N` pins the worker count.
 * Both exist for the same reason: `ParallelDeterminismTest` compares a serial build against a parallel one
 * and two parallel builds at different widths, and without a way to ask for a specific arrangement that
 * test cannot be written.
 */
object Parallel {

  /**
   * Cell count below which a grid loop stays on one thread.
   *
   * The zone-server generates a 128x128 world at boot - 16384 cells, a few milliseconds a stage. Handing
   * that to twelve threads costs more in wakeups than the loop costs to run, so the small world keeps the
   * code path it has today and only the big offline worlds pay for a pool at all.
   */
  const val MIN_CELLS = 8192L

  val enabled: Boolean = System.getProperty("worldgen.parallel")?.toBoolean() ?: true

  val threads: Int = (System.getProperty("worldgen.threads")?.toIntOrNull()
    ?: Runtime.getRuntime().availableProcessors()).coerceAtLeast(1)

  /**
   * Whether this thread is already inside a parallel region.
   *
   * A nested split runs serially rather than throwing. Throwing would be the stricter choice and it is the
   * wrong one here: the outer region is usually the profitable one - all twelve workers are already busy
   * laying out twelve towns - so an inner split has nothing to gain and, on a fixed pool, everything to
   * lose. A worker that blocks waiting for tasks queued behind it on the same pool is a deadlock.
   */
  private val nested = ThreadLocal.withInitial { false }

  private val pool: ExecutorService by lazy {
    val counter = AtomicInteger()
    Executors.newFixedThreadPool(threads) { runnable ->
      // Daemon, so a finished ViewerMain still exits and the zone-server is not held open by idle workers.
      Thread(runnable, "worldgen-worker-${counter.getAndIncrement()}").apply { isDaemon = true }
    }
  }

  /**
   * Splits `0 until height` into contiguous bands and runs [body] on each.
   *
   * Contiguous rather than interleaved because a band walks a run of a row-major array and interleaving
   * would have every worker touching every cache line. Exactly [threads] bands rather than many small ones
   * because grid rows cost the same as each other, so there is nothing for a finer split to balance.
   *
   * [body] must write only to rows in `yFrom until yUntil` and must read nothing that another band writes.
   */
  inline fun rows(height: Int, width: Int, crossinline body: (yFrom: Int, yUntil: Int) -> Unit) {
    val bands = bandsFor(height, height.toLong() * width)
    if (bands <= 1) {
      body(0, height)
      return
    }

    val perBand = (height + bands - 1) / bands
    runAll((0 until bands).mapNotNull { band ->
      val from = band * perBand
      val until = minOf(height, from + perBand)
      if (from >= until) null else Callable { body(from, until) }
    })
  }

  /**
   * Runs [body] for every index in `0 until count` and returns the results **in index order**.
   *
   * For the coarse, independent, wildly uneven units of work - laying out a town, routing a road between a
   * pair of settlements. Unlike [rows] this hands work out through a shared cursor rather than in fixed
   * bands, because a capital and a hamlet differ by orders of magnitude and fixed bands would leave eleven
   * workers waiting on whichever one drew the capital.
   *
   * That makes completion order genuinely arbitrary, which is exactly why results go into a pre-sized array
   * at their own index instead of being appended to a list. The caller sees index order every time, and the
   * architecture document's rule against thread-completion-order accumulation is kept by construction
   * rather than by remembering to sort afterwards.
   */
  fun <T> map(count: Int, body: (index: Int) -> T): List<T> {
    if (count <= 0) return emptyList()
    if (count == 1 || !enabled || threads == 1 || nested.get()) {
      return (0 until count).map(body)
    }

    val results = arrayOfNulls<Any?>(count)
    val cursor = AtomicInteger(0)
    val workers = min(threads, count)

    runAll((0 until workers).map {
      Callable {
        while (true) {
          val i = cursor.getAndIncrement()
          if (i >= count) break
          results[i] = body(i)
        }
      }
    })

    @Suppress("UNCHECKED_CAST")
    return results.asList() as List<T>
  }

  /**
   * Runs [body] with every split on this thread forced back to a serial loop.
   *
   * [threads] and [enabled] are read from system properties once at class init, so they cannot be moved
   * inside a running JVM - and `ParallelDeterminismTest` has to build the same world both ways in one JVM
   * to compare them. This reuses the nesting guard rather than adding a second switch, which also means
   * the serial path the test exercises is the same one a nested split takes in production.
   */
  fun <T> serially(body: () -> T): T {
    val was = nested.get()
    nested.set(true)
    try {
      return body()
    } finally {
      nested.set(was)
    }
  }

  /** Public because [rows] is inline; not meant to be called directly. */
  fun bandsFor(height: Int, cells: Long): Int {
    if (!enabled || threads == 1 || height < 2 || cells < MIN_CELLS || nested.get()) return 1
    return min(threads, height)
  }

  /**
   * Public because [rows] is inline; not meant to be called directly.
   *
   * Marks the thread as being inside a region for the duration, on the workers *and* on the caller, so a
   * split reached from inside [body] degrades to a serial loop rather than queueing behind itself.
   */
  fun runAll(tasks: List<Callable<*>>) {
    if (tasks.isEmpty()) return
    if (tasks.size == 1) {
      tasks[0].call()
      return
    }

    val guarded = tasks.map { task ->
      Callable {
        nested.set(true)
        try {
          task.call()
        } finally {
          nested.set(false)
        }
      }
    }

    nested.set(true)
    val futures = try {
      pool.invokeAll(guarded)
    } finally {
      nested.set(false)
    }

    // Unwrap, so a stage's own IllegalStateException arrives as itself rather than buried in an
    // ExecutionException - the invariant checks and `require` blocks throughout this module are the main
    // way a bad world announces itself, and a wrapped one reads as an infrastructure fault instead.
    for (future in futures) {
      try {
        future.get()
      } catch (e: ExecutionException) {
        throw e.cause ?: e
      }
    }
  }
}
