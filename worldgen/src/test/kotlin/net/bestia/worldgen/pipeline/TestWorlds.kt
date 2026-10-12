package net.bestia.worldgen.pipeline

import net.bestia.worldgen.core.Parallel
import net.bestia.worldgen.core.WorldConfig
import java.lang.ref.SoftReference
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Worlds built once and shared by every test class that asks for the same one.
 *
 * A world is a pure function of its [WorldConfig] and [WorldParams] and is immutable once built, so two classes
 * that both want seed 7 at 128 cells have no reason to generate it twice. They did: ten classes built exactly
 * that world, four built seed 909 at 160, and `ManaHistoryTest` built the same eight seeds in each of its five
 * tests. Building was most of what `:worldgen:test` spent its time on, so this is where the suite got faster.
 *
 * ### What belongs here and what does not
 *
 * A **fixture** - a world a test reads - goes through [get]. A test whose claim *is* that building twice agrees
 * (`StandardWorldTest.the world is reproducible from its seed`, `ParallelDeterminismTest`, the determinism case in
 * `NavGraphStageTest` and `HistoryStageTest`) must keep calling `StandardWorld.build` itself: handed a cached
 * world, it would compare a world with itself and pass whatever the generator did. Worlds large enough to be
 * the point of a test (`WorldSizeSanityTest`) are not shared either, since nothing else asks for them and
 * holding them would only cost heap.
 *
 * ### Memory
 *
 * Held by [SoftReference], so the collector may drop a world under pressure and the next caller rebuilds it.
 * That is the right failure for a cache whose entries are all reproducible, and the reason a class that wants a
 * world for its whole run keeps its own strong reference (`by lazy`) instead of calling [get] each time.
 * [sweep] does not cache at all: its point is to reduce a world to a summary and let it go.
 *
 * ### Concurrency
 *
 * Safe for the JUnit class parallelism `junit-platform.properties` turns on. Each world has a slot of its own, so
 * two classes asking for the same one wait for a single build rather than starting two, while different worlds
 * build side by side. Parallel builds run on a pool of this object's own and not on `Parallel`'s: a worker there
 * that blocked on a slot another thread was filling, while that thread waited for a band queued behind it on the
 * same pool, would be a deadlock. Builds on this pool go through [Parallel.serially], for the reason
 * `Invariants.sweep` gives - twelve whole worlds at once keeps every core busy where splitting each leaves them
 * idle through the serial stages.
 *
 * **Call [getAll] and [sweep] from a `by lazy` or a test method, never from an eager initialiser.** A plain
 * `val` in a companion object runs inside the outer class's static initialiser, and the pool's threads then
 * block on that class - to reach a helper in the companion - while the thread initialising it blocks on them.
 */
object TestWorlds {

  private data class Key(val config: WorldConfig, val params: WorldParams)

  private class Slot {
    private var world: SoftReference<GeneratedWorld>? = null

    @Synchronized
    fun get(build: () -> GeneratedWorld): GeneratedWorld {
      world?.get()?.let { return it }
      return build().also { world = SoftReference(it) }
    }

    @Synchronized
    fun peek(): GeneratedWorld? = world?.get()
  }

  private val slots = ConcurrentHashMap<Key, Slot>()

  /**
   * Worlds in flight at once. Eight rather than one per core because each is a working set of its own, and the
   * suite shares a 4 GB heap with whatever else JUnit is running beside it.
   */
  private val width = if (!Parallel.enabled) 1 else Parallel.threads.coerceIn(1, 8)

  private val pool by lazy {
    val counter = AtomicInteger()
    Executors.newFixedThreadPool(width) { task ->
      Thread(task, "test-worlds-${counter.getAndIncrement()}").apply { isDaemon = true }
    }
  }

  /** The world for [config] and [params], built on first request. */
  fun get(config: WorldConfig, params: WorldParams = WorldParams.DEFAULT): GeneratedWorld =
    slots.computeIfAbsent(Key(config, params)) { Slot() }.get { StandardWorld.build(config, params = params) }

  /** [get] for several worlds, building the ones nobody has built yet side by side. Results are in [configs] order. */
  fun getAll(configs: List<WorldConfig>, params: WorldParams = WorldParams.DEFAULT): List<GeneratedWorld> =
    inParallel(configs.size) { get(configs[it], params) }

  /**
   * Builds every config, applies [reduce] to each world as soon as it exists and keeps only what that returns.
   *
   * For the tests that count something over a couple of dozen seeds. Keeping the worlds would hold all of them
   * in memory at once for the sake of one number each, which is why `PoiStageTest` always reduced as it built.
   * A world already in the cache is used rather than rebuilt; nothing built here is added to it.
   */
  fun <T> sweep(
    configs: List<WorldConfig>,
    params: WorldParams = WorldParams.DEFAULT,
    reduce: (GeneratedWorld) -> T
  ): List<T> = inParallel(configs.size) { i ->
    val config = configs[i]
    val cached = slots[Key(config, params)]?.peek()
    reduce(cached ?: StandardWorld.build(config, params = params))
  }

  /** Runs `body(0 until count)` across the pool and returns the results in index order. */
  private fun <T> inParallel(count: Int, body: (Int) -> T): List<T> {
    if (count <= 1 || width == 1) return List(count, body)

    val futures = List(count) { i -> pool.submit(Callable { Parallel.serially { body(i) } }) }
    return futures.map {
      try {
        it.get()
      } catch (e: ExecutionException) {
        // Unwrapped for the reason `Parallel.runAll` gives: a `require` in a stage should arrive as itself.
        throw e.cause ?: e
      }
    }
  }
}
