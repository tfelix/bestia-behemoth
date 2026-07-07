package net.bestia.zone.ecs2

import java.util.concurrent.ForkJoinPool

/**
 * Owns the registered systems, decides which are due each tick, and executes
 * them respecting both their [Schedule] and their read/write conflicts.
 *
 * Systems are grouped into ordered *waves*. Systems within a wave are mutually
 * non-conflicting and may run in parallel (when [parallel] is enabled); waves
 * run sequentially. A system is always placed in a wave strictly later than any
 * earlier-registered conflicting system, which preserves ordering for dependent
 * systems while still allowing independent ones to share a wave.
 */
class SystemScheduler(private val parallel: Boolean = false) {

  private class Entry(val system: Ecs2System) {
    var tickCounter = 0
    var accumulator = 0f
  }

  private val entries = ArrayList<Entry>()
  private var waves: List<List<Entry>> = emptyList()
  private val pool: ForkJoinPool? = if (parallel) ForkJoinPool.commonPool() else null

  val systemCount: Int get() = entries.size

  /** Number of parallel waves; exposed for testing/introspection. */
  val waveCount: Int get() = waves.size

  fun register(system: Ecs2System) {
    entries.add(Entry(system))
    recomputeWaves()
  }

  fun registerAll(systems: Iterable<Ecs2System>) {
    systems.forEach { entries.add(Entry(it)) }
    recomputeWaves()
  }

  fun tick(world: World, deltaTime: Float) {
    // Evaluate due-ness exactly once per entry (this mutates cadence counters).
    val due = HashSet<Entry>()
    for (e in entries) {
      if (isDue(e, deltaTime)) due.add(e)
    }
    if (due.isEmpty()) return

    for (wave in waves) {
      val toRun = wave.filter { it in due }
      if (toRun.isEmpty()) continue

      if (pool == null || toRun.size == 1) {
        toRun.forEach { it.system.update(world, deltaTime) }
      } else {
        // Run this wave's systems concurrently and wait for completion before
        // advancing to the next (dependent) wave.
        pool.submit {
          toRun.parallelStream().forEach { it.system.update(world, deltaTime) }
        }.get()
      }
    }
  }

  private fun isDue(e: Entry, deltaTime: Float): Boolean = when (val s = e.system.schedule) {
    is Schedule.EveryTick -> true
    is Schedule.EveryTicks -> {
      e.tickCounter++
      if (e.tickCounter >= s.n) {
        e.tickCounter = 0
        true
      } else false
    }

    is Schedule.EverySeconds -> {
      e.accumulator += deltaTime
      if (e.accumulator >= s.seconds) {
        e.accumulator -= s.seconds
        true
      } else false
    }
  }

  private fun recomputeWaves() {
    val waveIndexOf = HashMap<Entry, Int>()
    val result = ArrayList<MutableList<Entry>>()

    for (e in entries) {
      // Earliest wave allowed = one past the latest conflicting earlier system.
      var minWave = 0
      for ((other, w) in waveIndexOf) {
        if (conflicts(other.system, e.system)) {
          minWave = maxOf(minWave, w + 1)
        }
      }
      while (result.size <= minWave) result.add(mutableListOf())
      result[minWave].add(e)
      waveIndexOf[e] = minWave
    }

    waves = result.filter { it.isNotEmpty() }
  }

  private fun conflicts(a: Ecs2System, b: Ecs2System): Boolean =
    a.writes.any { it in b.reads || it in b.writes } ||
      b.writes.any { it in a.reads || it in a.writes }
}
